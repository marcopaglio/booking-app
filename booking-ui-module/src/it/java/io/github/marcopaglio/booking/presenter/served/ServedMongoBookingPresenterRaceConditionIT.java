package io.github.marcopaglio.booking.presenter.served;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static io.github.marcopaglio.booking.model.Client.CLIENT_TABLE_DB;
import static io.github.marcopaglio.booking.model.Reservation.RESERVATION_TABLE_DB;
import static io.github.marcopaglio.booking.repository.mongo.MongoRepository.BOOKING_DB_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.bson.UuidRepresentation.STANDARD;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.bson.codecs.pojo.Conventions.ANNOTATION_CONVENTION;
import static org.bson.codecs.pojo.Conventions.USE_GETTERS_FOR_SETTERS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.github.marcopaglio.booking.model.Client;
import io.github.marcopaglio.booking.model.Reservation;
import io.github.marcopaglio.booking.repository.factory.ClientRepositoryFactory;
import io.github.marcopaglio.booking.repository.factory.ReservationRepositoryFactory;
import io.github.marcopaglio.booking.service.transactional.TransactionalBookingService;
import io.github.marcopaglio.booking.transaction.handler.factory.TransactionHandlerFactory;
import io.github.marcopaglio.booking.transaction.manager.mongo.TransactionMongoManager;
import io.github.marcopaglio.booking.validator.ClientValidator;
import io.github.marcopaglio.booking.validator.ReservationValidator;
import io.github.marcopaglio.booking.view.BookingView;

@DisplayName("Integration tests of race conditions for ServedBookingPresenter and MongoDB")
class ServedMongoBookingPresenterRaceConditionIT {
	private static final int NUM_OF_THREADS = 10;

	private static final String A_LASTNAME = "Rossi";
	private static final String A_FIRSTNAME = "Mario";
	private static final UUID A_CLIENT_UUID = UUID.fromString("03ee257d-f06d-47e9-8ef0-78b18ee03fe9");
	private static final String ANOTHER_LASTNAME = "De Lucia";
	private static final String ANOTHER_FIRSTNAME = "Maria";

	private static final String A_DATE = "2023-04-24";
	private static final LocalDate A_LOCALDATE = LocalDate.parse(A_DATE);
	private static final UUID A_RESERVATION_UUID = UUID.fromString("a2014dc9-7f77-4aa2-a3ce-0559736a7670");
	private static final String ANOTHER_DATE = "2023-09-05";
	private static final LocalDate ANOTHER_LOCALDATE = LocalDate.parse(ANOTHER_DATE);

	private static MongoClient mongoClient;

	private static MongoDatabase database;
	private static MongoCollection<Client> clientCollection;
	private static MongoCollection<Reservation> reservationCollection;

	private TransactionMongoManager transactionMongoManager;
	private TransactionHandlerFactory transactionHandlerFactory;
	private ClientRepositoryFactory clientRepositoryFactory;
	private ReservationRepositoryFactory reservationRepositoryFactory;

	private TransactionalBookingService transactionalBookingService;

	@Mock
	private BookingView view;

	@Mock
	private ClientValidator clientValidator;

	@Mock
	private ReservationValidator reservationValidator;

	private Client client;
	private Reservation reservation;

	private AutoCloseable closeable;

	@BeforeAll
	static void setupClient() throws Exception {
		mongoClient = getClient(System.getProperty("mongo.connectionString", "mongodb://localhost:27017"));
		database = mongoClient.getDatabase(BOOKING_DB_NAME);
		clientCollection = database.getCollection(CLIENT_TABLE_DB, Client.class);
		reservationCollection = database.getCollection(RESERVATION_TABLE_DB, Reservation.class);
	}

	private static MongoClient getClient(String connectionString) {
		// define the CodecProvider for POJO classes
		CodecProvider pojoCodecProvider = PojoCodecProvider.builder()
				.conventions(Arrays.asList(ANNOTATION_CONVENTION, USE_GETTERS_FOR_SETTERS))
				.automatic(true)
				.build();
		
		// define the CodecRegistry as codecs and other related information
		CodecRegistry pojoCodecRegistry =
				fromRegistries(getDefaultCodecRegistry(),
				fromProviders(pojoCodecProvider));
		
		// configure the MongoClient for using the CodecRegistry
		MongoClientSettings settings = MongoClientSettings.builder()
				.applyConnectionString(new ConnectionString(connectionString))
				.uuidRepresentation(STANDARD)
				.codecRegistry(pojoCodecRegistry)
				.build();
		return MongoClients.create(settings);
	}

	@BeforeEach
	void setUp() throws Exception {
		closeable = MockitoAnnotations.openMocks(this);
		
		transactionHandlerFactory = new TransactionHandlerFactory();
		clientRepositoryFactory = new ClientRepositoryFactory();
		reservationRepositoryFactory = new ReservationRepositoryFactory();
		transactionMongoManager = new TransactionMongoManager(mongoClient, transactionHandlerFactory,
				clientRepositoryFactory, reservationRepositoryFactory);
		
		transactionalBookingService = new TransactionalBookingService(transactionMongoManager);
		
		// make sure we always start with a clean database
		database.drop();
		
		client = new Client(A_FIRSTNAME, A_LASTNAME);
		reservation = new Reservation(A_CLIENT_UUID, A_LOCALDATE);
	}

	@AfterEach
	void tearDown() throws Exception {
		closeable.close();
	}

	@AfterAll
	static void closeClient() throws Exception {
		mongoClient.close();
	}

	@Test
	@DisplayName("Concurrent requests of 'addClient'")
	void testAddClientWhenConcurrentRequestsOccurShouldAddOnceAndNotThrowShowingErrors() {
		when(clientValidator.validateFirstName(A_FIRSTNAME)).thenReturn(A_FIRSTNAME);
		when(clientValidator.validateLastName(A_LASTNAME)).thenReturn(A_LASTNAME);
		
		List<Thread> threads = IntStream.range(0, NUM_OF_THREADS)
				.mapToObj(i -> new Thread(() ->
						new ServedBookingPresenter(view, transactionalBookingService,
								clientValidator, reservationValidator)
							.addClient(A_FIRSTNAME, A_LASTNAME)))
				.peek(t -> t.start())
				.collect(Collectors.toList());
		
		await().atMost(10, SECONDS)
			.until(() -> threads.stream().noneMatch(t -> t.isAlive()));
		
		assertThat(readAllClientsFromDatabase()).containsOnlyOnce(client);
		
		verify(view, times(NUM_OF_THREADS-1)).showOperationError(AdditionalMatchers.or(
				eq("A client named " + A_FIRSTNAME + " " + A_LASTNAME + " has already been made."),
				eq("Something went wrong while adding " + new Client(A_FIRSTNAME, A_LASTNAME).toString() + ".")));
	}

	@Test
	@DisplayName("Concurrent requests of 'addReservation'")
	void testAddReservationWhenConcurrentRequestsOccurShouldAddOnceAndNotThrowShowingErrors() {
		addTestClientToDatabase(client, A_CLIENT_UUID);
		
		when(reservationValidator.validateClientId(A_CLIENT_UUID)).thenReturn(A_CLIENT_UUID);
		when(reservationValidator.validateDate(A_DATE)).thenReturn(A_LOCALDATE);
		
		List<Thread> threads = IntStream.range(0, NUM_OF_THREADS)
				.mapToObj(i -> new Thread(() ->
						new ServedBookingPresenter(view, transactionalBookingService,
								clientValidator, reservationValidator)
							.addReservation(client, A_DATE)))
				.peek(t -> t.start())
				.collect(Collectors.toList());
		
		await().atMost(10, SECONDS)
			.until(() -> threads.stream().noneMatch(t -> t.isAlive()));
		
		assertThat(readAllReservationsFromDatabase()).containsOnlyOnce(reservation);
		
		verify(view, times(NUM_OF_THREADS-1)).showOperationError(AdditionalMatchers.or(
				eq("A reservation on " + A_DATE + " has already been made."),
				eq("Something went wrong while adding " + new Reservation(A_CLIENT_UUID, A_LOCALDATE).toString() + ".")));
	}

	@Test
	@DisplayName("Concurrent requests of 'deleteClient'")
	void testDeleteClientWhenConcurrentRequestsOccurShouldDeleteOnceAndNotThrowShowingErrors() {
		addTestClientToDatabase(client, A_CLIENT_UUID);
		
		List<Thread> threads = IntStream.range(0, NUM_OF_THREADS)
				.mapToObj(i -> new Thread(() ->
						new ServedBookingPresenter(view, transactionalBookingService,
								clientValidator, reservationValidator)
							.deleteClient(client)))
				.peek(t -> t.start())
				.collect(Collectors.toList());
		
		await().atMost(10, SECONDS)
			.until(() -> threads.stream().noneMatch(t -> t.isAlive()));
		
		assertThat(readAllClientsFromDatabase()).doesNotContain(client);
		
		verify(view, times(NUM_OF_THREADS-1)).showOperationError(contains(client.toString()));
	}

	@Test
	@DisplayName("Concurrent requests of 'deleteReservation'")
	void testDeleteReservationWhenConcurrentRequestsOccurShouldDeleteOnceAndNotThrowShowingErrors() {
		addTestReservationToDatabase(reservation, A_RESERVATION_UUID);
		
		List<Thread> threads = IntStream.range(0, NUM_OF_THREADS)
				.mapToObj(i -> new Thread(() ->
						new ServedBookingPresenter(view, transactionalBookingService,
								clientValidator, reservationValidator)
							.deleteReservation(reservation)))
				.peek(t -> t.start())
				.collect(Collectors.toList());
		
		await().atMost(10, SECONDS)
			.until(() -> threads.stream().noneMatch(t -> t.isAlive()));
		
		assertThat(readAllReservationsFromDatabase()).doesNotContain(reservation);
		
		verify(view, times(NUM_OF_THREADS-1)).showOperationError(contains(reservation.toString()));
	}

	@Test
	@DisplayName("Concurrent requests of 'renameClient'")
	void testRenameClientWhenConcurrentRequestsOccurShouldRenameOnceAndNotThrowShowingErrors() {
		List<Client> clients = new ArrayList<>();
		IntStream.range(0, NUM_OF_THREADS).forEach(i -> {
			Client a_client = new Client(A_FIRSTNAME + i, A_LASTNAME + i);
			addTestClientToDatabase(a_client, UUID.randomUUID());
			clients.add(i, a_client);
		});
		
		when(clientValidator.validateFirstName(ANOTHER_FIRSTNAME)).thenReturn(ANOTHER_FIRSTNAME);
		when(clientValidator.validateLastName(ANOTHER_LASTNAME)).thenReturn(ANOTHER_LASTNAME);
		
		List<Thread> threads = IntStream.range(0, NUM_OF_THREADS)
				.mapToObj(i -> new Thread(() ->
						new ServedBookingPresenter(view, transactionalBookingService,
								clientValidator, reservationValidator)
							.renameClient(clients.get(i), ANOTHER_FIRSTNAME, ANOTHER_LASTNAME)))
				.peek(t -> t.start())
				.collect(Collectors.toList());
		
		await().atMost(10, SECONDS)
			.until(() -> threads.stream().noneMatch(t -> t.isAlive()));
		
		assertThat(readAllClientsFromDatabase())
			.containsOnlyOnce(new Client(ANOTHER_FIRSTNAME, ANOTHER_LASTNAME));
		
		verify(view, times(NUM_OF_THREADS-1)).showOperationError(anyString());
	}

	@Test
	@DisplayName("Concurrent requests of 'rescheduleReservation'")
	void testRescheduleReservationWhenConcurrentRequestsOccurShouldRescheduleOnceAndNotThrowShowingErrors() {
		addTestClientToDatabase(client, A_CLIENT_UUID);
		List<Reservation> reservations = new ArrayList<>();
		IntStream.range(0, NUM_OF_THREADS).forEach(i -> {
			Reservation a_reservation = new Reservation(A_CLIENT_UUID, LocalDate.of(i, 4, 24));
			addTestReservationToDatabase(a_reservation, UUID.randomUUID());
			reservations.add(i, a_reservation);
		});
		
		when(reservationValidator.validateDate(ANOTHER_DATE)).thenReturn(ANOTHER_LOCALDATE);
		
		List<Thread> threads = IntStream.range(0, NUM_OF_THREADS)
				.mapToObj(i -> new Thread(() ->
						new ServedBookingPresenter(view, transactionalBookingService,
								clientValidator, reservationValidator)
							.rescheduleReservation(reservations.get(i), ANOTHER_DATE)))
				.peek(t -> t.start())
				.collect(Collectors.toList());
		
		await().atMost(10, SECONDS)
			.until(() -> threads.stream().noneMatch(t -> t.isAlive()));
		
		assertThat(readAllReservationsFromDatabase())
			.containsOnlyOnce(new Reservation(A_CLIENT_UUID, ANOTHER_LOCALDATE));
		
		verify(view, times(NUM_OF_THREADS-1)).showOperationError(anyString());
	}

	private List<Client> readAllClientsFromDatabase() {
		return StreamSupport
				.stream(clientCollection.find().spliterator(), false)
				.collect(Collectors.toList());
	}

	private List<Reservation> readAllReservationsFromDatabase() {
		return StreamSupport
				.stream(reservationCollection.find().spliterator(), false)
				.collect(Collectors.toList());
	}

	public void addTestClientToDatabase(Client client, UUID id) {
		client.setId(id);
		clientCollection.insertOne(client);
	}

	public void addTestReservationToDatabase(Reservation reservation, UUID id) {
		reservation.setId(id);
		reservationCollection.insertOne(reservation);
	}
}