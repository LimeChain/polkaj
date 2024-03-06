use robusta_jni::bridge;

pub use self::jni::TranscriptData;

#[bridge]
pub mod jni {
  use robusta_jni::convert::{
    Field, Signature, TryFromJavaValue,
  };
  use robusta_jni::jni::objects::AutoLocal;

  #[derive(Signature, TryFromJavaValue)]
  #[package(io.emeraldpay.polkaj.merlin)]
  pub struct TranscriptData<'env: 'borrow, 'borrow> {
      #[allow(dead_code)]
      #[instance]
      raw: AutoLocal<'env, 'borrow>,

      //TODO:
      //  Figure out how to make this simply a `Box<[u8]>` instead of a `Vec<Box<[u8]>>`,
      //  currently a blocker with byte[] fields (seems to be an issue with robusta itself, some missing impls?)
      #[field]
      pub domainSeparationLabel: Field<'env, 'borrow, Vec<Box<[u8]>>>,

      #[field]
      pub labels: Field<'env, 'borrow, Vec<Box<[u8]>>>,

      #[field]
      pub messages: Field<'env, 'borrow, Vec<Box<[u8]>>>,
  }
}

// TODO:
//  Try to avoid having to `Box::leak`,
//  In theory, should be safe enough if used correctly Java-side, since "we know Java owns the object"
impl <'env, 'borrow> From<TranscriptData<'env, 'borrow>> for merlin::Transcript {
  fn from(value: TranscriptData) -> Self {
    let mut transcript = merlin::Transcript::new(Box::leak(
      value.domainSeparationLabel.get().unwrap()
      .first()
      .cloned()
      .unwrap()
    ));

    let labels = value.labels.get().unwrap();
    let messages = value.messages.get().unwrap();

    for (label, message) in labels.iter().cloned().zip(messages.iter().cloned()) {
      transcript.append_message(Box::leak(label), message.as_ref())
    }

    transcript
  }
}