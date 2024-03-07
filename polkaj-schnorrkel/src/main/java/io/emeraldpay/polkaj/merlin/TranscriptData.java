package io.emeraldpay.polkaj.merlin;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A container class to simply hold all the necessary input data (label + messages) to construct the actual
 * transcript on Rust's side. Think of this as "the bag of currently necessary arguments".
 * The main idea of this class is to be easily portable to Rust using JNI.
 * It makes no sense on its own on the Java side alone.
 */
public class TranscriptData {
    // INTENTIONALITY: Those fields being of type ArrayList is essential for the JNI mappings

    private final ArrayList<byte[]> domainSeparationLabel;

    private final ArrayList<byte[]> labels;
    private final ArrayList<byte[]> messages;

    public TranscriptData(byte[] domainSeparationLabel) {
        this.domainSeparationLabel = new ArrayList<>();
        this.domainSeparationLabel.add(domainSeparationLabel);
        this.labels = new ArrayList<>();
        this.messages = new ArrayList<>();
    }

    public void appendMessage(String label, String message) {
        appendMessage(label.getBytes(StandardCharsets.US_ASCII), message.getBytes(StandardCharsets.US_ASCII));
    }

    public void appendMessage(byte[] label, byte[] message) {
        labels.add(label);
        messages.add(message);
    }

    /**
     * Appends a message with an u64 body.
     * <br>
     * NOTE: only the last 8 bytes of the BigInteger's byte[]
     *       representation will be taken and reversed into little-endian order
     * @param label the message label
     * @param x the u64 to append
     */
    // TODO:
    //  Maybe just remove this method and leave it up to the user...
    //  We can't guarantee good enough type safety and obeying the representational contract is left to the developer,
    //  which could lead to issues.
    public void appendAsU64(byte[] label, BigInteger x) {
        appendMessage(label, encodeBigIntAsU64LittleEndian(x));
    }

    private static byte[] encodeBigIntAsU64LittleEndian(BigInteger x) {
        final int length = 8;
        byte[] byteArray = x.toByteArray();
        byte[] littleEndian = new byte[length];

        final int upperBound = Math.min(length, byteArray.length);
        for (int i = 0; i < upperBound; i++) {
            littleEndian[i] = byteArray[byteArray.length - 1 - i];
        }

        return littleEndian;
    }

    public static void main(String[] args) {
        long x = 0x1023456789ab00efL;
        BigInteger y = BigInteger.valueOf(x);

        System.out.println(Arrays.toString(y.toByteArray()));
        System.out.println(Arrays.toString(encodeBigIntAsU64LittleEndian(y)));
    }
}
