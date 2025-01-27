package io.emeraldpay.polkaj.types;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * A 256 bit value, commonly used as a hash
 */
public class Hash256 extends FixedBytes implements Comparable<Hash256>, Serializable {

    /**
     * Length in bytes (32 byte)
     */
    public static final int SIZE_BYTES = 32;

    /**
     * Create a new value. Makes sure the input is correct, if not throws an exception
     *
     * @param value 32 byte value
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException is size is not 32 bytes
     */
    public Hash256(byte[] value) {
        super(value, SIZE_BYTES);
    }

    /**
     * Creates an empty zeroed instance
     *
     * @return empty hash
     */
    public static Hash256 empty() {
        return new Hash256(new byte[SIZE_BYTES]);
    }

    /**
     * Parse hex value and create a new instance
     *
     * @param hex hex value, may optionally start with 0x prefix
     * @return hash instance
     * @throws IllegalArgumentException if value has invalid length
     * @throws NumberFormatException    if value has invalid format (non-hex characters, etc)
     */
    public static Hash256 from(String hex) {
        byte[] parsed = parseHex(hex, SIZE_BYTES);
        return new Hash256(parsed);
    }

    @Override
    public int compareTo(Hash256 o) {
        return super.compareTo(o);
    }

    /**
     * Custom serialization logic. Writes the length and value of the underlying byte array for the hash.
     *
     * @param out the {@link ObjectOutputStream} to write this object to
     * @throws IOException if an I/O error occurs while writing to the stream
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(value.length);
        out.write(value);
    }

    /**
     * Custom deserialization logic. Validates the required length and sets the underlying byte array using reflection.
     *
     * @param in the {@link ObjectInputStream} to read this object from
     * @throws IOException            if the length of the serialized data is invalid or if an I/O error occurs
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        if (length != SIZE_BYTES) {
            throw new IOException("Invalid Hash256 length: " + length);
        }
        byte[] value = new byte[length];
        in.readFully(value);
        try {
            java.lang.reflect.Field valueField = ByteData.class.getDeclaredField("value");
            valueField.setAccessible(true);
            valueField.set(this, value.clone());
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new IOException("Failed to initialize Hash256", e);
        }
    }
}
