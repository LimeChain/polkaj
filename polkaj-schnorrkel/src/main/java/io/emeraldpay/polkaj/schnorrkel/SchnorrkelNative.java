package io.emeraldpay.polkaj.schnorrkel;

import io.emeraldpay.polkaj.merlin.TranscriptData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Wrapper around Rust implementation of the algorithms.
 * <br>
 * See also:
 * <ul>
 *     <li><a href="https://github.com/w3f/schnorrkel">Rust implementatiion</a></li>
 *     <li><a href="https://tools.ietf.org/html/rfc8032">RFC 8032 - Edwards-Curve Digital Signature Algorithm (EdDSA)</a></li>
 * </ul>
 */
public class SchnorrkelNative extends Schnorrkel {

    private static final String LIBNAME = "polkaj_schnorrkel";

    static {

        try {
            // JVM needs native libraries to be loaded from filesystem, so first we need to extract
            // files for current OS into a temp dir then load the file.
            if (!extractAndLoadJNI()) {
                // load the native library, this is for running tests
                System.loadLibrary(LIBNAME);
            }
        } catch (IOException e) {
            System.err.println("Failed to extract JNI library from Jar file. " + e.getClass() + ":" + e.getMessage());
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load native library. Polkaj Schnorrkel methods are unavailable. Error: " + e.getMessage());
        }
    }

    private static Schnorrkel.KeyPair decodeKeyPair(byte[] key) throws SchnorrkelException {
        if (key.length != KEYPAIR_LENGTH) {
            throw new SchnorrkelException("Invalid key generated");
        }
        byte[] secretKey = new byte[SECRET_KEY_LENGTH];
        System.arraycopy(key, 0, secretKey, 0, SECRET_KEY_LENGTH);
        byte[] publicKey = new byte[PUBLIC_KEY_LENGTH];
        System.arraycopy(key, SECRET_KEY_LENGTH, publicKey, 0, PUBLIC_KEY_LENGTH);

        return new Schnorrkel.KeyPair(publicKey, secretKey);
    }

    private static byte[] encodeKeyPair(Schnorrkel.KeyPair keyPair) {
        byte[] result = new byte[KEYPAIR_LENGTH];
        System.arraycopy(keyPair.getSecretKey(), 0, result, 0, keyPair.getSecretKey().length);
        System.arraycopy(keyPair.getPublicKey(), 0, result, SECRET_KEY_LENGTH, keyPair.getPublicKey().length);
        return result;
    }

    // ====================== Mapping to the Native Library ======================

    private static native byte[] sign(byte[] publicKey, byte[] secretKey, byte[] message);

    private static native byte[] keypairFromSeed(byte[] seed);

    private static native boolean verify(byte[] signature, byte[] message, byte[] publicKey);

    private static native boolean verifyDeprecated(byte[] signature, byte[] message, byte[] publicKey);

    private static native byte[] deriveHard(byte[] secret, byte[] cc);

    private static native byte[] deriveSoft(byte[] secret, byte[] cc);

    private static native byte[] derivePublicKeySoft(byte[] publicKey, byte[] cc);

    private static native boolean vrfVerify(byte[] publicKey, TranscriptData transcript, byte[] vrfOutput, byte[] vrfProof);

    private static native byte[] vrfSign(byte[] secretKey, TranscriptData transcript);

    private static native byte[] makeBytes(byte[] publicKey, TranscriptData transcript, byte[] vrfOutput);

    private static boolean extractAndLoadJNI() throws IOException {
        // define which of files bundled with Jar to extract
        String os = System.getProperty("os.name", "unknown").toLowerCase();
        if (os.contains("win")) {
            os = "windows/amd";
        } else if (os.contains("mac")) {
            String arch = System.getProperty("os.arch").toLowerCase();
            if (arch.equalsIgnoreCase("aarch64")) {
                os = "macos/arm";
            } else {
                os = "macos/amd";
            }
        } else if (os.contains("nux")) {
            String arch = System.getProperty("os.arch").toLowerCase();
            if (arch.equalsIgnoreCase("aarch64")) {
                os = "linux/arm";
            } else if (arch.equalsIgnoreCase("amd64")) {
                os = "linux/amd";
            } else {
                os = "linux/i686";
            }
        } else {
            System.err.println("Unknown OS: " + os + ". Unable to setup native library for Polkaj Schnorrkel");
            return false;
        }
        String filename = System.mapLibraryName(LIBNAME);
        String classpathFile = "/native/" + os + "/" + filename;

        // extract native lib to the filesystem
        InputStream lib = Schnorrkel.class.getResourceAsStream(classpathFile);
        if (lib == null) {
            System.err.println("Library " + classpathFile + " is not found in the classpath");
            return false;
        }
        Path dir = Files.createTempDirectory(LIBNAME);
        Path target = dir.resolve(filename);

        Files.copy(lib, target);
        System.load(target.toFile().getAbsolutePath());
        System.out.println("Library " + classpathFile + " is loaded");

        // setup JVM to delete files on exit, when possible
        target.toFile().deleteOnExit();
        dir.toFile().deleteOnExit();
        return true;
    }

    @Override
    public byte[] sign(byte[] message, KeyPair keypair) throws SchnorrkelException {
        return SchnorrkelNative.sign(keypair.getPublicKey(), keypair.getSecretKey(), message);
    }

    @Override
    public boolean verify(byte[] signature, byte[] message, PublicKey publicKey) throws SchnorrkelException {
        return SchnorrkelNative.verify(signature, message, publicKey.getPublicKey());
    }

    @Override
    public boolean verifyDeprecated(byte[] signature, byte[] message, PublicKey publicKey) throws SchnorrkelException {
        return SchnorrkelNative.verifyDeprecated(signature, message, publicKey.getPublicKey());
    }

    @Override
    public KeyPair generateKeyPair() throws SchnorrkelException {
        try {
            return generateKeyPair(SecureRandom.getInstanceStrong());
        } catch (NoSuchAlgorithmException e) {
            throw new SchnorrkelException("Secure Random is not available");
        }
    }

    @Override
    public KeyPair generateKeyPair(SecureRandom random) throws SchnorrkelException {
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        byte[] key = keypairFromSeed(seed);
        return decodeKeyPair(key);
    }

    @Override
    public KeyPair generateKeyPairFromSeed(byte[] seed) throws SchnorrkelException {
        byte[] key = keypairFromSeed(seed);
        return decodeKeyPair(key);
    }

    // ====================== LOAD NATIVE LIBRARY ======================

    @Override
    public KeyPair deriveKeyPair(KeyPair base, ChainCode chainCode) throws SchnorrkelException {
        byte[] key = deriveHard(encodeKeyPair(base), chainCode.getValue());
        return decodeKeyPair(key);
    }

    @Override
    public KeyPair deriveKeyPairSoft(KeyPair base, ChainCode chainCode) throws SchnorrkelException {
        byte[] key = deriveSoft(encodeKeyPair(base), chainCode.getValue());
        return decodeKeyPair(key);
    }

    @Override
    public PublicKey derivePublicKeySoft(PublicKey base, ChainCode chainCode) throws SchnorrkelException {
        byte[] key = derivePublicKeySoft(base.getPublicKey(), chainCode.getValue());
        return new Schnorrkel.PublicKey(key);
    }

    @Override
    public boolean vrfVerify(PublicKey pk, TranscriptData transcript, VrfOutputAndProof vrfOutputAndProof) {
        return vrfVerify(
            pk.getPublicKey(),
            transcript,
            vrfOutputAndProof.getOutput(),
            vrfOutputAndProof.getProof()
        );
    }

    @Override
    public VrfOutputAndProof vrfSign(KeyPair keyPair, TranscriptData transcript) {
        byte[] vrfOutputAndProofBytes = vrfSign(keyPair.getSecretKey(), transcript);
        final int OUTPUT_LEN = VrfOutputAndProof.OUTPUT_BYTE_LEN;
        final int PROOF_LEN = VrfOutputAndProof.PROOF_BYTE_LEN;

        // effectively, split the array
        byte[] vrfOutput = new byte[OUTPUT_LEN];
        byte[] vrfProof = new byte[PROOF_LEN];
        System.arraycopy(vrfOutputAndProofBytes, 0, vrfOutput, 0, OUTPUT_LEN);
        System.arraycopy(vrfOutputAndProofBytes, OUTPUT_LEN, vrfProof, 0, PROOF_LEN);

        return VrfOutputAndProof.wrap(vrfOutput, vrfProof);
    }

    @Override
    public byte[] makeBytes(Schnorrkel.PublicKey publicKey, TranscriptData transcript, VrfOutputAndProof vrfOutputAndProof) {
        byte[] vrfOutput = vrfOutputAndProof.getOutput();
        return makeBytes(publicKey.getPublicKey(), transcript, vrfOutput);
    }
}
