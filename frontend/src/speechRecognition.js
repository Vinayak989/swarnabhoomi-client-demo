export function speechRecognitionConstructor() {
  if (typeof window === 'undefined' || window.isSecureContext === false) return null;
  return window.SpeechRecognition || window.webkitSpeechRecognition || null;
}

export function speechUnavailableNotice() {
  return 'या ब्राउझरमध्ये थेट आवाज ओळख उपलब्ध नाही. मजकूर पेटीवर टॅप करा आणि फोनच्या कीबोर्डवरील माइक वापरा.';
}

export function speechErrorNotice(error) {
  switch (error) {
    case 'not-allowed':
    case 'service-not-allowed':
      return 'फोन आणि ब्राउझरच्या साइट सेटिंग्जमध्ये मायक्रोफोनची परवानगी द्या. नंतर पुन्हा प्रयत्न करा.';
    case 'audio-capture':
      return 'मायक्रोफोन सुरू झाला नाही. फोनमधील माइक आणि त्याची परवानगी तपासा.';
    case 'network':
      return 'आवाज ओळख सेवा मिळाली नाही. इंटरनेट तपासा किंवा फोनच्या कीबोर्डवरील माइक वापरा.';
    case 'language-not-supported':
      return 'या ब्राउझरमध्ये मराठी आवाज ओळख उपलब्ध नाही. फोनच्या कीबोर्डवरील माइक वापरा.';
    case 'no-speech':
      return 'आवाज ऐकू आला नाही. माइकजवळ बोलून पुन्हा प्रयत्न करा.';
    case 'aborted':
      return '';
    default:
      return 'आवाज ओळखता आला नाही. पुन्हा प्रयत्न करा किंवा फोनच्या कीबोर्डवरील माइक वापरा.';
  }
}
