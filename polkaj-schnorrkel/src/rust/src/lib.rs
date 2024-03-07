//
// Based on https://github.com/polkadot-js/wasm/blob/master/packages/wasm-crypto/src/sr25519.rs
//

extern crate schnorrkel;
extern crate hex;
extern crate rand;
extern crate robusta_jni;
extern crate merlin;

mod merlin_jni;

use merlin::Transcript;
use robusta_jni::jni::objects::JObject;
use robusta_jni::jni::JNIEnv;
use robusta_jni::jni::objects::JClass;
use robusta_jni::jni::sys::{jboolean, jbyteArray};
use schnorrkel::vrf::{VRFInOut, VRFProof, VRFProofBatchable};
use schnorrkel::{SecretKey, PublicKey, Signature, SignatureError, MiniSecretKey, ExpansionMode, Keypair};
use schnorrkel::derive::{ChainCode, CHAIN_CODE_LENGTH, Derivation};
use std::string::String;

const SIGNING_CTX: &'static [u8] = b"substrate";

/// ChainCode construction helper
fn create_cc(data: &[u8]) -> ChainCode {
    let mut cc = [0u8; CHAIN_CODE_LENGTH];

    cc.copy_from_slice(&data);

    ChainCode(cc)
}

fn sign(message: Vec<u8>, sk: Vec<u8>, pubkey: Vec<u8>) -> Result<Vec<u8>, String> {
    let pubkey = PublicKey::from_bytes(pubkey.as_slice())
        .map_err(|e| e.to_string())?;

    let signature = SecretKey::from_ed25519_bytes(sk.as_slice())
        .map_err(|e| e.to_string())?
        .sign_simple(SIGNING_CTX,
                     message.as_slice(),
                     &pubkey)
        .to_bytes()
        .to_vec();
    Ok(signature)
}

fn verify(signature: &[u8], message: &[u8], public: &[u8]) -> Result<bool, String> {
    let signature = Signature::from_bytes(signature)
        .map_err(|e| e.to_string())?;
    let result = PublicKey::from_bytes(public)
        .map_err(|e| e.to_string())?
        .verify_simple(SIGNING_CTX, message, &signature)
        .map(|_| true);
    match result {
        Ok(value) => Ok(value),
        Err(err) => match err {
            SignatureError::EquationFalse => Ok(false),
            _ => Err(err.to_string())
        }
    }
}

fn keypair_from_seed(seed: &[u8]) -> Result<Vec<u8>, String> {
    let result = MiniSecretKey::from_bytes(seed)
        .map_err(|e| e.to_string())?
        .expand_to_keypair(ExpansionMode::Ed25519)
        .to_half_ed25519_bytes()
        .to_vec();
    Ok(result)
}

pub fn derive_keypair_hard(pair: &[u8], cc: &[u8]) -> Result<Vec<u8>, String> {
    let result = Keypair::from_half_ed25519_bytes(pair)
        .map_err(|e| e.to_string())?
        .secret
        .hard_derive_mini_secret_key(Some(create_cc(cc)), &[]).0
        .expand_to_keypair(ExpansionMode::Ed25519)
        .to_half_ed25519_bytes()
        .to_vec();
    Ok(result)
}

pub fn derive_keypair_soft(pair: &[u8], cc: &[u8]) -> Result<Vec<u8>, String> {
    let result = Keypair::from_half_ed25519_bytes(pair)
        .map_err(|e| e.to_string())?
        .derived_key_simple(create_cc(cc), &[]).0
        .to_half_ed25519_bytes()
        .to_vec();
    Ok(result)
}

pub fn derive_pubkey_soft(pubkey: &[u8], cc: &[u8]) -> Result<Vec<u8>, String> {
    let result = 	PublicKey::from_bytes(pubkey)
        .map_err(|e| e.to_string())?
        .derived_key_simple(create_cc(cc), &[]).0
        .to_bytes()
        .to_vec();
    Ok(result)
}

#[no_mangle]
pub extern "system" fn Java_io_emeraldpay_polkaj_schnorrkel_SchnorrkelNative_sign
    (env: JNIEnv, _class: JClass, pubkey: jbyteArray, sk: jbyteArray, message: jbyteArray) -> jbyteArray {

    let message = env.convert_byte_array(message)
        .expect("Message is not provided");
    let sk = env.convert_byte_array(sk)
        .expect("Secret Key is not provided");
    let pubkey = env.convert_byte_array(pubkey)
        .expect("Public Key is not provided");

    let output = match sign(message, sk, pubkey) {
        Ok(signature) => {
            env.byte_array_from_slice(signature.as_slice())
                .expect("Couldn't create result")
        },
        Err(msg) => {
            let none = env.new_byte_array(0)
                .expect("Couldn't create empty result");
            env.throw_new("io/emeraldpay/polkaj/schnorrkel/SchnorrkelException", msg).unwrap();
            none
        }
    };
    output
}

#[no_mangle]
pub extern "system" fn Java_io_emeraldpay_polkaj_schnorrkel_SchnorrkelNative_verify
(env: JNIEnv, _class: JClass, signature: jbyteArray, message: jbyteArray, pubkey: jbyteArray) -> jboolean {

    let message = env.convert_byte_array(message)
        .expect("Message is not provided");
    let pubkey = env.convert_byte_array(pubkey)
        .expect("Public Key is not provided");
    let signature = env.convert_byte_array(signature)
        .expect("Signature is not provided");

    let output = match verify(signature.as_slice(), message.as_slice(), pubkey.as_slice()) {
        Ok(valid) => {
            valid as jboolean
        },
        Err(msg) => {
            let none = false as jboolean;
            env.throw_new("io/emeraldpay/polkaj/schnorrkel/SchnorrkelException", msg).unwrap();
            none
        }
    };
    output
}

#[no_mangle]
pub extern "system" fn Java_io_emeraldpay_polkaj_schnorrkel_SchnorrkelNative_keypairFromSeed
(env: JNIEnv, _class: JClass, seed: jbyteArray) -> jbyteArray {

    let seed = env.convert_byte_array(seed)
        .expect("Seed is not provided");

    let output = match keypair_from_seed(seed.as_slice()) {
        Ok(value) => {
            env.byte_array_from_slice(value.as_slice())
                .expect("Couldn't create result")
        },
        Err(msg) => {
            let none = env.new_byte_array(0)
                .expect("Couldn't create empty result");
            env.throw_new("io/emeraldpay/polkaj/schnorrkel/SchnorrkelException", msg).unwrap();
            none
        }
    };
    output
}

#[no_mangle]
pub extern "system" fn Java_io_emeraldpay_polkaj_schnorrkel_SchnorrkelNative_deriveHard
(env: JNIEnv, _class: JClass, keypair: jbyteArray, cc: jbyteArray) -> jbyteArray {

    let keypair = env.convert_byte_array(keypair)
        .expect("Keypair is not provided");
    let cc = env.convert_byte_array(cc)
        .expect("ChainCode is not provided");

    let output = match derive_keypair_hard(keypair.as_slice(), cc.as_slice()) {
        Ok(value) => {
            env.byte_array_from_slice(value.as_slice())
                .expect("Couldn't create result")
        },
        Err(msg) => {
            let none = env.new_byte_array(0)
                .expect("Couldn't create empty result");
            env.throw_new("io/emeraldpay/polkaj/schnorrkel/SchnorrkelException", msg).unwrap();
            none
        }
    };
    output
}

#[no_mangle]
pub extern "system" fn Java_io_emeraldpay_polkaj_schnorrkel_SchnorrkelNative_deriveSoft
(env: JNIEnv, _class: JClass, keypair: jbyteArray, cc: jbyteArray) -> jbyteArray {

    let keypair = env.convert_byte_array(keypair)
        .expect("Keypair is not provided");
    let cc = env.convert_byte_array(cc)
        .expect("ChainCode is not provided");

    let output = match derive_keypair_soft(keypair.as_slice(), cc.as_slice()) {
        Ok(value) => {
            env.byte_array_from_slice(value.as_slice())
                .expect("Couldn't create result")
        },
        Err(msg) => {
            let none = env.new_byte_array(0)
                .expect("Couldn't create empty result");
            env.throw_new("io/emeraldpay/polkaj/schnorrkel/SchnorrkelException", msg).unwrap();
            none
        }
    };
    output
}

#[no_mangle]
pub extern "system" fn Java_io_emeraldpay_polkaj_schnorrkel_SchnorrkelNative_derivePublicKeySoft
(env: JNIEnv, _class: JClass, pubkey: jbyteArray, cc: jbyteArray) -> jbyteArray {

    let pubkey = env.convert_byte_array(pubkey)
        .expect("Keypair is not provided");
    let cc = env.convert_byte_array(cc)
        .expect("ChainCode is not provided");

    let output = match derive_pubkey_soft(pubkey.as_slice(), cc.as_slice()) {
        Ok(value) => {
            env.byte_array_from_slice(value.as_slice())
                .expect("Couldn't create result")
        },
        Err(msg) => {
            let none = env.new_byte_array(0)
                .expect("Couldn't create empty result");
            env.throw_new("io/emeraldpay/polkaj/schnorrkel/SchnorrkelException", msg).unwrap();
            none
        }
    };
    output
}

use merlin_jni::TranscriptData;
use robusta_jni::convert::{IntoJavaValue, TryFromJavaValue};

#[no_mangle]
pub extern "system" fn Java_io_emeraldpay_polkaj_schnorrkel_SchnorrkelNative_vrfVerify(
    env: JNIEnv,
    _class: JClass,
    pk_raw: jbyteArray,
    transcript_data_raw: JObject,
    vrf_output_raw: jbyteArray,
    vrf_proof_raw: jbyteArray
) -> jboolean {
    let pk_bytes = env.convert_byte_array(pk_raw).expect("Public key bytes not provided.");

    let transcript_data = match TranscriptData::try_from(transcript_data_raw, &env) {
        Ok(data) => data,
        Err(msg) => {
            env.throw_new("io/emeraldpay/polkaj/schnorrkel/SchnorrkelException", msg.to_string()).unwrap();
            return false as jboolean;
        },
    };

    let transcript = Transcript::from(transcript_data);

    let vrf_output_bytes = env.convert_byte_array(vrf_output_raw).expect("Vrf output bytes not provided.");

    let vrf_proof_bytes = env.convert_byte_array(vrf_proof_raw).expect("Vrf proof bytes not provided.");

    // TODO:
    // More sensibly differentiate between "false" as in "unsuccessful verification"
    // and an error as in "exception must be thrown (Java-side)"
    let result = vrf_verify(&pk_bytes, transcript, &vrf_output_bytes, &vrf_proof_bytes).is_ok();
    IntoJavaValue::into(result, &env)
}

fn vrf_verify(
    pk_bytes: &[u8],
    transcript: Transcript,
    vrf_output_bytes: &[u8],
    vrf_proof_bytes: &[u8]
) -> Result<(), SchnorrkelError> {
    let signing_public_key = schnorrkel::PublicKey::from_bytes(pk_bytes).map_err(SchnorrkelError)?;

    // NOTE:
    // These `from_bytes`s can only panic if `vrf_output_bytes` or `vrf_proof_bytes` are of the wrong
    // length, which is the Java caller's responsibility. In any case, errors are properly accounted for.
    let vrf_output = schnorrkel::vrf::VRFPreOut::from_bytes(vrf_output_bytes).map_err(SchnorrkelError)?;
    let vrf_proof = schnorrkel::vrf::VRFProof::from_bytes(&vrf_proof_bytes).map_err(SchnorrkelError)?;

    signing_public_key.vrf_verify(transcript, &vrf_output, &vrf_proof).map_err(SchnorrkelError)?;
    Ok(())
}

struct SchnorrkelError(SignatureError);

#[no_mangle]
pub extern "system" fn Java_io_emeraldpay_polkaj_schnorrkel_SchnorrkelNative_vrfSign(
    env: JNIEnv,
    _class: JClass,
    sk_raw: jbyteArray,
    transcript_data_raw: JObject,
) -> jbyteArray {
    let sk_bytes = env.convert_byte_array(sk_raw).expect("Secret key bytes not provided.");

    let transcript_data = match TranscriptData::try_from(transcript_data_raw, &env) {
        Ok(data) => data,
        Err(msg) => {
            env.throw_new("io/emeraldpay/polkaj/schnorrkel/SchnorrkelException", msg.to_string()).unwrap();
            return *JObject::null();
        },
    };

    let transcript = Transcript::from(transcript_data);

    match vrf_sign(&sk_bytes, transcript) {
        Err(SchnorrkelError(err)) => {
            env.throw_new("io/emeraldpay/polkaj/schnorrkel/SchnorrkelException", err.to_string()).unwrap();
            *JObject::null()
        },
        Ok((vrf_in_out, vrf_proof, _)) => {
            let output_bytes = vrf_in_out.output.to_bytes();
            let proof_bytes = vrf_proof.to_bytes();
            // TODO:
            //  Figure out a better serialization (or a JNI mapping...),
            //  for now, concatting the two byte arrays suffices, but it's horribly inextensible.
            let whole: Vec<u8> = output_bytes.iter().chain(proof_bytes.iter()).map(|v| *v).collect();
            env.byte_array_from_slice(whole.as_slice())
                .expect("Couldn't create result")
        },
    }
}

fn vrf_sign(sk_bytes: &[u8], transcript: Transcript) -> Result<(VRFInOut, VRFProof, VRFProofBatchable), SchnorrkelError> {
    let sk = SecretKey::from_ed25519_bytes(&sk_bytes).map_err(SchnorrkelError)?;
    let keypair = sk.to_keypair();
    Ok(keypair.vrf_sign(transcript))
}