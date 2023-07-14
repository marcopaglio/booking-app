package io.github.marcopaglio.booking.transaction.manager.mongo;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static io.github.marcopaglio.booking.repository.mongo.ClientMongoRepository.BOOKING_DB_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.bson.UuidRepresentation.STANDARD;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.bson.codecs.pojo.Conventions.ANNOTATION_CONVENTION;
import static org.bson.codecs.pojo.Conventions.USE_GETTERS_FOR_SETTERS;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import io.github.marcopaglio.booking.exception.NotNullConstraintViolationException;
import io.github.marcopaglio.booking.exception.TransactionException;
import io.github.marcopaglio.booking.exception.UniquenessConstraintViolationException;
import io.github.marcopaglio.booking.model.Client;
import io.github.marcopaglio.booking.repository.ClientRepository;
import io.github.marcopaglio.booking.repository.mongo.ClientMongoRepository;
import io.github.marcopaglio.booking.repository.mongo.ReservationMongoRepository;
import io.github.marcopaglio.booking.transaction.code.ClientTransactionCode;

@DisplayName("Tests for TransactionMongoManager class")
@ExtendWith(MockitoExtension.class)
@Testcontainers
class TransactionMongoManagerTest {

	@Container
	private static final MongoDBContainer mongo = new MongoDBContainer("mongo:6.0.7");

	private static MongoClient mongoClient;
	private static ClientSession clientSession;
	private ClientSession spiedClientSession;
	private static MongoDatabase database;
	//private MongoCollection<Client> clientCollection;

	@Mock
	private ClientMongoRepository clientRepository;

	@Mock
	private ReservationMongoRepository reservationRepository;

	private TransactionMongoManager transactionManager;

	@BeforeAll
	public static void setupServer() throws Exception {
		mongoClient = getClient(mongo.getConnectionString());
		//mongoClient = MongoClients.create(mongo.getConnectionString());
		
		clientSession = mongoClient.startSession();
		
		database = mongoClient.getDatabase(BOOKING_DB_NAME);
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
		// make sure we always start with a clean database
		database.drop();
		
		spiedClientSession = spy(clientSession);
		
		// clientRepository = new ClientMongoRepository(mongoClient);
		//clientCollection = clientRepository.getCollection();
		
		transactionManager = new TransactionMongoManager(spiedClientSession, clientRepository, reservationRepository);
	}

	@AfterEach
	void closeSession() {
		//clientSession.close();
	}

	@AfterAll
	public static void shutdownServer() throws Exception {
		clientSession.close();
		mongoClient.close();
	}

	@Nested
	@DisplayName("Using ClientTransactionCode")
	class ClientTransactionCodeTest {

		private static final String A_FIRSTNAME = "Mario";
		private static final String A_LASTNAME = "Rossi";

		@Test
		@DisplayName("Code calls ClientRepository's method")
		void testDoInTransactionWhenCallsAMethodOfClientRepositoryShouldApplyAndReturn() {
			List<Client> listOfClients = Arrays.asList(new Client(A_FIRSTNAME, A_LASTNAME));
			when(clientRepository.findAll()).thenReturn(listOfClients);
			
			ClientTransactionCode<List<Client>> code = (ClientRepository clientRepository) ->
					clientRepository.findAll();
			
			assertThat(transactionManager.doInTransaction(code)).isEqualTo(listOfClients);
			
			InOrder inOrder = Mockito.inOrder(spiedClientSession, clientRepository);
			
			inOrder.verify(spiedClientSession).startTransaction();
			inOrder.verify(clientRepository).findAll();
			inOrder.verify(spiedClientSession).commitTransaction();
			
			assertThat(spiedClientSession.hasActiveTransaction()).isFalse();
		}

		@Test
		@DisplayName("Code on ClientRepository throws IllegalArgumentException")
		void testDoInTransactionWhenClientRepositoryThrowsIllegalArgumentExceptionShouldAbortAndThrow() {
			doThrow(new IllegalArgumentException()).when(clientRepository).delete(null);

			ClientTransactionCode<Object> code = (ClientRepository clientRepository) -> {
				clientRepository.delete(null);
				return null;
			};
			
			assertThatThrownBy(() -> transactionManager.doInTransaction(code))
				.isInstanceOf(TransactionException.class)
				.hasMessage("Transaction fails due to invalid argument(s) passed.");
			
			verify(spiedClientSession, never()).commitTransaction();
			verify(spiedClientSession).abortTransaction();
		}

		@Test
		@DisplayName("Code on ClientRepository throws NotNullConstraintViolationException")
		void testDoInTransactionWhenClientRepositoryThrowsNotNullConstraintViolationExceptionShouldAbortAndThrow() {
			doThrow(new NotNullConstraintViolationException())
				.when(clientRepository).save(isA(Client.class));
			
			ClientTransactionCode<Client> code = (ClientRepository clientRepository) -> 
				clientRepository.save(new Client(A_FIRSTNAME, A_LASTNAME));
			
			assertThatThrownBy(() -> transactionManager.doInTransaction(code))
				.isInstanceOf(TransactionException.class)
				.hasMessage("Transaction fails due to violation of not-null constraint(s).");
			
			verify(spiedClientSession, never()).commitTransaction();
			verify(spiedClientSession).abortTransaction();
		}

		@Test
		@DisplayName("Code on ClientRepository throws UniquenessConstraintViolationException")
		void testDoInTransactionWhenClientRepositoryThrowsUniquenessConstraintViolationExceptionShouldAbortAndThrow() {
			doThrow(new UniquenessConstraintViolationException())
				.when(clientRepository).save(isA(Client.class));
			
			ClientTransactionCode<Client> code = (ClientRepository clientRepository) -> 
				clientRepository.save(new Client(A_FIRSTNAME, A_LASTNAME));
			
			assertThatThrownBy(() -> transactionManager.doInTransaction(code))
				.isInstanceOf(TransactionException.class)
				.hasMessage("Transaction fails due to violation of uniqueness constraint(s).");
			
			verify(spiedClientSession, never()).commitTransaction();
			verify(spiedClientSession).abortTransaction();
		}

		@Test
		@DisplayName("Code throws others RuntimeException")
		void testDoInTransactionWhenCodeThrowsOthersRuntimeExceptionsShouldAbortAndRethrow() {
			ClientTransactionCode<Object> code = (ClientRepository clientRepository) -> {
				throw new RuntimeException();
			};
			
			assertThatThrownBy(() -> transactionManager.doInTransaction(code))
				.isInstanceOf(RuntimeException.class);
			
			verify(spiedClientSession, never()).commitTransaction();
			verify(spiedClientSession).abortTransaction();
		}
	}

	@Nested
	@DisplayName("Using ReservationTransactionCode")
	class ReservationTransactionCodeTest {

		private static final String A_FIRSTNAME = "Mario";
		private static final String A_LASTNAME = "Rossi";

		@Test
		@DisplayName("Code calls ClientRepository's method")
		void testDoInTransactionWhenCallsAMethodOfClientRepositoryShouldApplyAndReturn() {
			List<Client> listOfClients = Arrays.asList(new Client(A_FIRSTNAME, A_LASTNAME));
			when(clientRepository.findAll()).thenReturn(listOfClients);
			
			ClientTransactionCode<List<Client>> code = (ClientRepository clientRepository) ->
					clientRepository.findAll();
			
			assertThat(transactionManager.doInTransaction(code)).isEqualTo(listOfClients);
			
			InOrder inOrder = Mockito.inOrder(spiedClientSession, clientRepository);
			
			inOrder.verify(spiedClientSession).startTransaction();
			inOrder.verify(clientRepository).findAll();
			inOrder.verify(spiedClientSession).commitTransaction();
			
			assertThat(spiedClientSession.hasActiveTransaction()).isFalse();
		}

		@Test
		@DisplayName("Code on ClientRepository throws IllegalArgumentException")
		void testDoInTransactionWhenClientRepositoryThrowsIllegalArgumentExceptionShouldAbortAndThrow() {
			doThrow(new IllegalArgumentException()).when(clientRepository).delete(null);

			ClientTransactionCode<Object> code = (ClientRepository clientRepository) -> {
				clientRepository.delete(null);
				return null;
			};
			
			assertThatThrownBy(() -> transactionManager.doInTransaction(code))
				.isInstanceOf(TransactionException.class)
				.hasMessage("Transaction fails due to invalid argument(s) passed.");
			
			verify(spiedClientSession, never()).commitTransaction();
			verify(spiedClientSession).abortTransaction();
		}

		@Test
		@DisplayName("Code on ClientRepository throws NotNullConstraintViolationException")
		void testDoInTransactionWhenClientRepositoryThrowsNotNullConstraintViolationExceptionShouldAbortAndThrow() {
			doThrow(new NotNullConstraintViolationException())
				.when(clientRepository).save(isA(Client.class));
			
			ClientTransactionCode<Client> code = (ClientRepository clientRepository) -> 
				clientRepository.save(new Client(A_FIRSTNAME, A_LASTNAME));
			
			assertThatThrownBy(() -> transactionManager.doInTransaction(code))
				.isInstanceOf(TransactionException.class)
				.hasMessage("Transaction fails due to violation of not-null constraint(s).");
			
			verify(spiedClientSession, never()).commitTransaction();
			verify(spiedClientSession).abortTransaction();
		}

		@Test
		@DisplayName("Code on ClientRepository throws UniquenessConstraintViolationException")
		void testDoInTransactionWhenClientRepositoryThrowsUniquenessConstraintViolationExceptionShouldAbortAndThrow() {
			doThrow(new UniquenessConstraintViolationException())
				.when(clientRepository).save(isA(Client.class));
			
			ClientTransactionCode<Client> code = (ClientRepository clientRepository) -> 
				clientRepository.save(new Client(A_FIRSTNAME, A_LASTNAME));
			
			assertThatThrownBy(() -> transactionManager.doInTransaction(code))
				.isInstanceOf(TransactionException.class)
				.hasMessage("Transaction fails due to violation of uniqueness constraint(s).");
			
			verify(spiedClientSession, never()).commitTransaction();
			verify(spiedClientSession).abortTransaction();
		}

		@Test
		@DisplayName("Code throws others RuntimeException")
		void testDoInTransactionWhenCodeThrowsOthersRuntimeExceptionsShouldAbortAndRethrow() {
			ClientTransactionCode<Object> code = (ClientRepository clientRepository) -> {
				throw new RuntimeException();
			};
			
			assertThatThrownBy(() -> transactionManager.doInTransaction(code))
				.isInstanceOf(RuntimeException.class);
			
			verify(spiedClientSession, never()).commitTransaction();
			verify(spiedClientSession).abortTransaction();
		}
	}
}
