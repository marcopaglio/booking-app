package io.github.marcopaglio.booking.service.transactional;

import static io.github.marcopaglio.booking.model.Client.CLIENT_TABLE_DB;
import static io.github.marcopaglio.booking.model.Reservation.RESERVATION_TABLE_DB;
import static io.github.marcopaglio.booking.service.transactional.TransactionalBookingService.CLIENT_ALREADY_EXISTS_ERROR_MSG;
import static io.github.marcopaglio.booking.service.transactional.TransactionalBookingService.CLIENT_NOT_FOUND_ERROR_MSG;
import static io.github.marcopaglio.booking.service.transactional.TransactionalBookingService.RESERVATION_ALREADY_EXISTS_ERROR_MSG;
import static io.github.marcopaglio.booking.service.transactional.TransactionalBookingService.RESERVATION_NOT_FOUND_ERROR_MSG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.marcopaglio.booking.exception.InstanceAlreadyExistsException;
import io.github.marcopaglio.booking.exception.InstanceNotFoundException;
import io.github.marcopaglio.booking.model.Client;
import io.github.marcopaglio.booking.model.Reservation;
import io.github.marcopaglio.booking.repository.factory.ClientRepositoryFactory;
import io.github.marcopaglio.booking.repository.factory.ReservationRepositoryFactory;
import io.github.marcopaglio.booking.transaction.handler.factory.TransactionHandlerFactory;
import io.github.marcopaglio.booking.transaction.manager.postgres.TransactionPostgresManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

@DisplayName("Integration tests of TransactionalBookingService and PostgreSQL")
class TransactionalPostgresBookingServiceIT {
	private static final String A_LASTNAME = "Rossi";
	private static final String A_FIRSTNAME = "Mario";
	private static final String ANOTHER_LASTNAME = "De Lucia";
	private static final String ANOTHER_FIRSTNAME = "Maria";

	private static final UUID A_CLIENT_UUID = UUID.fromString("78bce42b-1d28-4c37-b0a2-3287d6a829ca");
	private static final LocalDate A_LOCALDATE = LocalDate.parse("2023-04-24");
	private static final UUID ANOTHER_CLIENT_UUID = UUID.fromString("864f7928-049b-4c2a-bd87-9e52ca16afc5");
	private static final LocalDate ANOTHER_LOCALDATE = LocalDate.parse("2023-09-05");

	private Client client, another_client;
	private Reservation reservation, another_reservation;

	private static EntityManagerFactory emf;
	private static TransactionHandlerFactory transactionHandlerFactory;
	private static ClientRepositoryFactory clientRepositoryFactory;
	private static ReservationRepositoryFactory reservationRepositoryFactory;
	private static TransactionPostgresManager transactionManager;

	private TransactionalBookingService service;

	@BeforeAll
	static void setupCollaborators() throws Exception {
		System.setProperty("db.port", System.getProperty("postgres.port", "5432"));
		System.setProperty("db.name", System.getProperty("postgres.name", "IntegrationTest_db"));
		emf = Persistence.createEntityManagerFactory("postgres-it");
		
		transactionHandlerFactory = new TransactionHandlerFactory();
		clientRepositoryFactory = new ClientRepositoryFactory();
		reservationRepositoryFactory = new ReservationRepositoryFactory();
		transactionManager = new TransactionPostgresManager(emf, transactionHandlerFactory,
				clientRepositoryFactory, reservationRepositoryFactory);
	}

	@BeforeEach
	void setUp() throws Exception {
		EntityManager em = emf.createEntityManager();
		// make sure we always start with a clean database
		em.getTransaction().begin();
		em.createNativeQuery("TRUNCATE TABLE " + CLIENT_TABLE_DB + "," + RESERVATION_TABLE_DB).executeUpdate();
		em.getTransaction().commit();
		em.close();
		
		service = new TransactionalBookingService(transactionManager);
	}

	@AfterAll
	static void closeEmf() throws Exception {
		emf.close();
	}

	@Nested
	@DisplayName("Methods using only ClientPostgresRepository")
	class ClientPostgresRepositoryIT {

		@BeforeEach
		void initClients() throws Exception {
			client = new Client(A_FIRSTNAME, A_LASTNAME);
			another_client = new Client(ANOTHER_FIRSTNAME, ANOTHER_LASTNAME);
		}

		@Nested
		@DisplayName("Integration tests for 'findAllClients'")
		class FindAllClientsIT {

			@Test
			@DisplayName("No clients to retrieve")
			void testFindAllClientsWhenThereAreNoClientsToRetrieveShouldReturnEmptyList() {
				assertThat(service.findAllClients()).isEmpty();
			}

			@Test
			@DisplayName("Several clients to retrieve")
			void testFindAllClientsWhenThereAreSeveralClientsToRetrieveShouldReturnClientsAsList() {
				addTestClientToDatabase(client);
				addTestClientToDatabase(another_client);
				
				assertThat(service.findAllClients())
					.isEqualTo(Arrays.asList(client, another_client));
			}
		}

		@Nested
		@DisplayName("Integration tests for 'findClient'")
		class FindClientIT {

			@Test
			@DisplayName("Client exists")
			void testFindClientWhenClientExistsShouldReturnTheClient() {
				addTestClientToDatabase(client);
				
				assertThat(service.findClient(client.getId())).isEqualTo(client);
			}

			@Test
			@DisplayName("Client doesn't exist")
			void testFindClientWhenClientDoesNotExistShouldThrow() {
				assertThatThrownBy(() -> service.findClient(A_CLIENT_UUID))
					.isInstanceOf(InstanceNotFoundException.class)
					.hasMessage(CLIENT_NOT_FOUND_ERROR_MSG);
			}
		}

		@Nested
		@DisplayName("Integration tests for 'findClientNamed'")
		class FindClientNamedIT {

			@Test
			@DisplayName("Client exists")
			void testFindClientNamedWhenClientExistsShouldReturnTheClient() {
				addTestClientToDatabase(client);
				
				assertThat(service.findClientNamed(A_FIRSTNAME, A_LASTNAME)).isEqualTo(client);
			}

			@Test
			@DisplayName("Client doesn't exist")
			void testFindClientNamedWhenClientDoesNotExistShouldThrow() {
				assertThatThrownBy(() -> service.findClientNamed(A_FIRSTNAME, A_LASTNAME))
					.isInstanceOf(InstanceNotFoundException.class)
					.hasMessage(CLIENT_NOT_FOUND_ERROR_MSG);
			}
		}

		@Nested
		@DisplayName("Integration tests for 'insertNewClient'")
		class InsertNewClientIT {

			@Test
			@DisplayName("Client is new")
			void testInsertNewClientWhenClientDoesNotAlreadyExistShouldInsertAndReturnWithId() {
				Client clientInDB = service.insertNewClient(client);
				
				assertThat(clientInDB).isEqualTo(client);
				assertThat(clientInDB.getId()).isNotNull();
				assertThat(readAllClientsFromDatabase()).containsExactly(client);
			}

			@Test
			@DisplayName("Client already exists")
			void testInsertNewClientWhenClientAlreadyExistsShouldNotInsertAndThrow() {
				Client existingClient = new Client(A_FIRSTNAME, A_LASTNAME);
				addTestClientToDatabase(existingClient);
				UUID existingId = existingClient.getId();
				
				assertThatThrownBy(() -> service.insertNewClient(client))
					.isInstanceOf(InstanceAlreadyExistsException.class)
					.hasMessage(CLIENT_ALREADY_EXISTS_ERROR_MSG);
				
				List<Client> clientsInDB = readAllClientsFromDatabase();
				assertThat(clientsInDB).containsExactly(existingClient);
				assertThat(clientsInDB.get(0).getId()).isEqualTo(existingId);
			}

			private List<Client> readAllClientsFromDatabase() {
				EntityManager em = emf.createEntityManager();
				List<Client> clientsInDB = em.createQuery("SELECT c FROM Client c", Client.class).getResultList();
				em.close();
				return clientsInDB;
			}
		}
	}

	@Nested
	@DisplayName("Methods using only ReservationPostgresRepository")
	class ReservationPostgresRepositoryIT {

		@BeforeEach
		void initReservations() throws Exception {
			reservation = new Reservation(A_CLIENT_UUID, A_LOCALDATE);
			another_reservation = new Reservation(ANOTHER_CLIENT_UUID, ANOTHER_LOCALDATE);
		}

		@Nested
		@DisplayName("Integration tests for 'findAllReservations'")
		class FindAllReservationsIT {

			@Test
			@DisplayName("No reservations to retrieve")
			void testFindAllReservationsWhenThereAreNoReservationsToRetrieveShouldReturnEmptyList() {
				assertThat(service.findAllReservations()).isEmpty();
			}

			@Test
			@DisplayName("Several reservations to retrieve")
			void testFindAllReservationsWhenThereAreSeveralReservationsToRetrieveShouldReturnReservationAsList() {
				addTestReservationToDatabase(reservation);
				addTestReservationToDatabase(another_reservation);
				
				assertThat(service.findAllReservations())
					.isEqualTo(Arrays.asList(reservation, another_reservation));
			}
		}

		@Nested
		@DisplayName("Integration tests for 'findReservation'")
		class FindReservationIT {

			@DisplayName("Reservation exists")
			@Test
			void testFindReservationWhenReservationExistsShouldReturnTheReservation() {
				addTestReservationToDatabase(reservation);
				
				assertThat(service.findReservation(reservation.getId())).isEqualTo(reservation);
			}

			@Test
			@DisplayName("Reservation doesn't exist")
			void testFindReservationWhenReservationDoesNotExistShouldThrow() {
				UUID notPresentId = UUID.randomUUID();
				assertThatThrownBy(() -> service.findReservation(notPresentId))
					.isInstanceOf(InstanceNotFoundException.class)
					.hasMessage(RESERVATION_NOT_FOUND_ERROR_MSG);
			}
		}

		@Nested
		@DisplayName("Integration tests for 'findReservationOn'")
		class FindReservationOnIT {

			@DisplayName("Reservation exists")
			@Test
			void testFindReservationOnWhenReservationExistsShouldReturnTheReservation() {
				addTestReservationToDatabase(reservation);
				
				assertThat(service.findReservationOn(A_LOCALDATE)).isEqualTo(reservation);
			}

			@Test
			@DisplayName("Reservation doesn't exist")
			void testFindReservationOnWhenReservationDoesNotExistShouldThrow() {
				assertThatThrownBy(() -> service.findReservationOn(A_LOCALDATE))
					.isInstanceOf(InstanceNotFoundException.class)
					.hasMessage(RESERVATION_NOT_FOUND_ERROR_MSG);
			}
		}
	}

	@Nested
	@DisplayName("Methods using both repositories")
	class BothRepositoriesIT {

		@BeforeEach
		void initEntities() throws Exception {
			client = new Client(A_FIRSTNAME, A_LASTNAME);
			another_client = new Client(ANOTHER_FIRSTNAME, ANOTHER_LASTNAME);
			reservation = new Reservation(A_CLIENT_UUID, A_LOCALDATE);
			another_reservation = new Reservation(ANOTHER_CLIENT_UUID, ANOTHER_LOCALDATE);
		}

		@Nested
		@DisplayName("Tests for 'insertNewReservation'")
		class InsertNewReservationIT {

			@DisplayName("Reservation is new and client exists")
			@Test
			void testInsertNewReservationWhenReservationIsNewAndAssociatedClientExistsShouldInsertAndReturnWithId() {
				addTestClientToDatabase(client);
				reservation.setClientId(client.getId());
				
				Reservation reservationInDB = service.insertNewReservation(reservation);
				
				assertThat(reservationInDB).isEqualTo(reservation);
				assertThat(reservationInDB.getId()).isNotNull();
				assertThat(readAllReservationsFromDatabase()).containsExactly(reservationInDB);
			}

			@Test
			@DisplayName("Reservation is new and client doesn't exist")
			void testInsertNewReservationWhenReservationIsNewAndAssociatedClientDoesNotExistShouldNotInsertAndThrow() {
				assertThatThrownBy(() -> service.insertNewReservation(reservation))
					.isInstanceOf(InstanceNotFoundException.class)
					.hasMessage(CLIENT_NOT_FOUND_ERROR_MSG);
				
				assertThat(readAllReservationsFromDatabase()).isEmpty();
			}

			@Test
			@DisplayName("Reservation already exists")
			void testInsertNewReservationWhenReservationAlreadyExistsShouldNotInsertAndThrow() {
				Reservation existingReservation = new Reservation(A_CLIENT_UUID, A_LOCALDATE);
				addTestReservationToDatabase(existingReservation);
				UUID existingId = existingReservation.getId();
				
				assertThatThrownBy(() -> service.insertNewReservation(reservation))
					.isInstanceOf(InstanceAlreadyExistsException.class)
					.hasMessage(RESERVATION_ALREADY_EXISTS_ERROR_MSG);
				
				List<Reservation> reservationsInDB = readAllReservationsFromDatabase();
				assertThat(reservationsInDB).containsExactly(existingReservation);
				assertThat(reservationsInDB.get(0).getId()).isEqualTo(existingId);
			}

			private List<Reservation> readAllReservationsFromDatabase() {
				EntityManager em = emf.createEntityManager();
				List<Reservation> reservationsInDB = em.createQuery("SELECT r FROM Reservation r", Reservation.class).getResultList();
				em.close();
				return reservationsInDB;
			}
		}
	}

	private void addTestClientToDatabase(Client client) {
		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		em.persist(client);
		em.getTransaction().commit();
		em.close();
	}

	private void addTestReservationToDatabase(Reservation reservation) {
		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		em.persist(reservation);
		em.getTransaction().commit();
		em.close();
	}
}
