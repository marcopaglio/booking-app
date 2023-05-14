package io.github.marcopaglio.booking.model;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import io.github.marcopaglio.booking.annotation.Generated;

/**
 * This entity represents the customer's model of the booking application.
 */
public class Client {
	/**
	 * Regular expression for stating other characters except the alphabetic ones and horizontal spaces.
	 */
	private static final Pattern notOnlyAlphabetic =
			Pattern.compile("[^\\p{IsAlphabetic}\\h]");

	/**
	 * The identifier of the client entity.
	 */
	private final UUID uuid;

	/**
	 * The name of the client entity.
	 * Note: the couple [{@code firstName}, {@code lastName}] is unique among client entities.
	 */
	private final String firstName;

	/**
	 * The surname of the client entity.
	 * Note: the couple [{@code firstName}, {@code lastName}] is unique in the client entity.
	 */
	private final String lastName;

	/**
	 * Constructs a client for the booking application with a name,
	 * a surname and a initial list of reservations.
	 * The constructor checks if the parameters are valid for the creation of the client.
	 * 
	 * @param firstName					the name of the client.
	 * @param lastName					the surname of the client.
	 * @throws IllegalArgumentException	if at least one of the argument is null or not valid.
	 */
	public Client(String firstName, String lastName)
			throws IllegalArgumentException {
		checkNameValidity(firstName, "name");
		checkNameValidity(lastName, "surname");
		
		this.firstName = removeExcessedSpaces(firstName);
		this.lastName = removeExcessedSpaces(lastName);
		
		this.uuid = UUID.randomUUID();
	}

	/**
	 * Checks if the string is a valid name that is not null neither empty,
	 * and contains only alphabetic and horizontal whitespace characters.
	 *
	 * @param name						the string to evaluate.
	 * @param inputName					the role of the string in the client's context.
	 * @throws IllegalArgumentException	if {@code name} is a null or empty string,
	 * 									or it contains non-alphabetic characters.
	 */
	private static void checkNameValidity(String name, String inputName) throws IllegalArgumentException {
		checkNotNull(name, inputName);
		checkNotEmpty(name, inputName);
		checkOnlyAlphabetic(name, inputName);
	}

	/**
	 * Checks if the object is not null.
	 *
	 * @param o							the object to evaluate.
	 * @param inputName					the role of the object in the client's context.
	 * @throws IllegalArgumentException	if {@code o} is null.
	 */
	private static void checkNotNull(Object o, String inputName) throws IllegalArgumentException {
		if (o == null)
			throw new IllegalArgumentException(
				"Client needs a not null " + inputName + ".");
	}

	/**
	 * Checks if the string is not empty.
	 *
	 * @param str						the string to evaluate.
	 * @param inputName					the role of the string in the client's context.
	 * @throws IllegalArgumentException	if {@code str} is empty.
	 */
	private static void checkNotEmpty(String str, String inputName) {
		if (str.trim().isEmpty()) 
			throw new IllegalArgumentException(
				"Client needs a non-empty " + inputName + ".");
	}

	/**
	 * Checks if the string contains only accepted characters that is
	 * (lower and upper) alphabetic and accented letters, and horizontal whitespace character.
	 *
	 * @param str						the string to evaluate.
	 * @param inputName					the role of the string in the client's context.
	 * @throws IllegalArgumentException	if {@code str} contains non-valid characters.
	 */
	private static void checkOnlyAlphabetic(String str, String inputName)
			throws IllegalArgumentException {
		if (notOnlyAlphabetic.matcher(str).find())
			throw new IllegalArgumentException(
				"Client's " + inputName + " must contain only alphabet letters.");
	}

	/**
	 * Removes side spaces and reduces multiple spaces into a single whitespace.
	 */
	private static String removeExcessedSpaces(String name) {
		return name.trim().replaceAll("\\s+", " ");
	}

	/**
	 * Retrieves the name of the client. Note: Java String Objects are immutable.
	 *
	 * @return	the {@code firstName} of the client.
	 */
	@Generated
	public final String getFirstName() {
		return this.firstName;
	}

	/**
	 * Retrieves the surname of the client. Note: Java String Objects are immutable.
	 *
	 * @return	the {@code lastName} of the client.
	 */
	@Generated
	public final String getLastName() {
		return this.lastName;
	}

	/**
	 * Retrieves the identifier of the client. Note: UUID Objects are immutable.
	 *
	 * @return	the {@code uuid} of the client.
	 */
	@Generated
	public final UUID getUuid() {
		return this.uuid;
	}

	/**
	 * Overridden method for returning a hash code value for the client object.
	 * 
	 * @return	a hash code value for this client object.
	 */
	@Generated
	@Override
	public int hashCode() {
		return Objects.hash(firstName, lastName);
	}

	/**
	 * Overridden method for indicating whether some other client object is "equal to" this one.
	 * Two client objects are equal if they have both the same name and surname.
	 * 
	 * @param obj	the reference client object with which to compare.
	 * @return		{@code true} if this object is the same as the {@code obj} argument;
	 * 				{@code false} otherwise.
	 */
	@Generated
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Client other = (Client) obj;
		return Objects.equals(firstName, other.firstName)
			&& Objects.equals(lastName, other.lastName);
	}

	/**
	 * Overridden method for returning a string representation of the client. 
	 *
	 * @return	a string representation of the client.
	 */
	@Generated
	@Override
	public String toString() {
		return "Client [" + firstName + " " + lastName + "]";
	}
}
