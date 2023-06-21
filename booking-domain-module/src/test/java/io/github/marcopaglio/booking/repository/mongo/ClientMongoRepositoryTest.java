package io.github.marcopaglio.booking.repository.mongo;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;

import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.github.marcopaglio.booking.model.Client;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.bson.UuidRepresentation.STANDARD;
import static org.bson.codecs.pojo.Conventions.ANNOTATION_CONVENTION;
import static org.bson.codecs.pojo.Conventions.USE_GETTERS_FOR_SETTERS;
import static io.github.marcopaglio.booking.repository.mongo.ClientMongoRepository.BOOKING_DB_NAME;

class ClientMongoRepositoryTest {

	private static MongoServer server;
	private static String connectionString;
	private static MongoClientSettings settings;
	private static MongoClient mongoClient;
	private static MongoDatabase database;

	private MongoCollection<Client> clientCollection;
	private ClientMongoRepository clientRepository;

	@BeforeAll
	public static void setupServer() throws Exception {
		server = new MongoServer(new MemoryBackend());
		
		// bind on a random local port
		connectionString = server.bindAndGetConnectionString();
		
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
		settings = MongoClientSettings.builder()
				.applyConnectionString(new ConnectionString(connectionString))
				.uuidRepresentation(STANDARD)
				.codecRegistry(pojoCodecRegistry)
				.build();
		mongoClient = MongoClients.create(settings);
		
		database = mongoClient.getDatabase(BOOKING_DB_NAME);
	}

	@BeforeEach
	void setUp() throws Exception {
		// make sure we always start with a clean database
		database.drop();
		
		// repository creation after drop because it removes configurations on collections
		clientRepository = new ClientMongoRepository(mongoClient);
		
		// get a MongoCollection suited for your POJO class
		clientCollection = clientRepository.getCollection();
	}

	@AfterAll
	public static void shutdownServer() throws Exception {
		mongoClient.close();
		server.shutdown();
	}

	/*@Test
	void testCollectionIsEmpty() {
		assertThat(clientCollection.countDocuments()).isZero();

		// make a document and insert it
		Client ada = new Client("Ada", "Byron");
		ada.setId(A_CLIENT_UUID);
		System.out.println("Original Person Model: " + ada);
		clientCollection.insertOne(ada);

		// Person will now have an ObjectId
		System.out.println("Mutated Person Model: " + ada);
		assertThat(clientCollection.countDocuments()).isEqualTo(1L);
		
		Client oda = new Client("Ada", "Byron");
		oda.setId(ANOTHER_CLIENT_UUID);
		System.out.println("Original Client Model: " + oda);
		//clientCollection.insertOne(oda);
		assertThatThrownBy(() -> clientCollection.insertOne(oda)).isInstanceOf(MongoWriteException.class);

		// get it (since it's the only one in there since we dropped the rest earlier on)
		Client somebody = clientCollection.find().first();
		System.out.println("Retrieved client: " + somebody);
		
		assertThat(somebody).isEqualTo(ada);
	}
	
	@Test
	void testCollectionIsEmpty2() {
		assertThat(clientCollection.countDocuments()).isZero();

		// make a document and insert it
		Client ada = new Client("Ada", "Byron");
		ada.setId(A_CLIENT_UUID);
		System.out.println("Original Person Model: " + ada);
		clientCollection.insertOne(ada);

		// Person will now have an ObjectId
		System.out.println("Mutated Person Model: " + ada);
		assertThat(clientCollection.countDocuments()).isEqualTo(1L);
		
		Client oda = new Client("Ada", "Byron");
		oda.setId(ANOTHER_CLIENT_UUID);
		System.out.println("Original Client Model: " + oda);
		assertThatThrownBy(() -> clientCollection.insertOne(oda)).isInstanceOf(MongoWriteException.class);

		// get it (since it's the only one in there since we dropped the rest earlier on)
		Client somebody = clientCollection.find().first();
		System.out.println("Retrieved client: " + somebody);
		
		assertThat(somebody).isEqualTo(ada);
	}*/
}
