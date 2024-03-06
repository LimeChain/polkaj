package io.emeraldpay.polkaj.merlin;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

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
    public void appendAsU64(byte[] label, BigInteger x) {
        final int U64_BYTE_LEN = 8;
        appendMessage(label, bytesToFixedLength(x.toByteArray(), U64_BYTE_LEN));
    }

    private static byte[] bytesToFixedLength(byte[] byteArray, int length) {
        byte[] littleEndian = new byte[length];

        for (int i = 0; i < length; i++) {
            littleEndian[i] = byteArray[byteArray.length - 1 - i];
        }

        return littleEndian;
    }
}
