import { cloneElement, isValidElement, useEffect, useRef, useState } from 'react';
import { Mic, MicOff } from 'lucide-react';
import { speechErrorNotice, speechRecognitionConstructor, speechUnavailableNotice } from './speechRecognition.js';
import './speechTextInput.css';

export default function SpeechTextInput({ children }) {
  if (!isValidElement(children) || !['input', 'textarea'].includes(children.type)) return children;
  const inputType = children.type === 'input' ? (children.props.type || 'text').toLowerCase() : 'text';
  if (!['text', 'search', 'tel'].includes(inputType) || children.props.readOnly || children.props.disabled || children.props.inputMode === 'numeric') return children;
  return <SpeechInputControl child={children}/>;
}

function SpeechInputControl({ child }) {
  const [listening, setListening] = useState(false);
  const [notice, setNotice] = useState('');
  const recognitionRef = useRef(null);
  const wrapperRef = useRef(null);
  const latestValue = useRef(String(child.props.value ?? child.props.defaultValue ?? ''));
  const onChangeRef = useRef(child.props.onChange);
  const supported = Boolean(speechRecognitionConstructor());
  onChangeRef.current = child.props.onChange;

  useEffect(() => {
    latestValue.current = String(child.props.value ?? child.props.defaultValue ?? '');
  }, [child.props.value, child.props.defaultValue]);
  useEffect(() => () => recognitionRef.current?.abort(), []);

  const onChange = event => {
    latestValue.current = event.target.value;
    onChangeRef.current?.(event);
  };

  function appendTranscript(transcript) {
    const spoken = transcript.trim();
    if (!spoken) return;
    const current = latestValue.current;
    const next = `${current}${current && !/\s$/.test(current) ? ' ' : ''}${spoken}`;
    const maxLength = child.props.maxLength == null ? Infinity : Number(child.props.maxLength);
    const value = Number.isFinite(maxLength) && maxLength >= 0 ? next.slice(0, maxLength) : next;
    latestValue.current = value;
    const target = { name: child.props.name, value };
    onChangeRef.current?.({ target, currentTarget: target });
  }

  function toggleListening(event) {
    event.preventDefault();
    event.stopPropagation();
    if (recognitionRef.current) {
      try { recognitionRef.current.stop(); } catch {}
      return;
    }
    const SpeechRecognition = speechRecognitionConstructor();
    if (!SpeechRecognition) {
      wrapperRef.current?.querySelector('input, textarea')?.focus();
      setNotice(speechUnavailableNotice());
      return;
    }

    let recognition;
    try { recognition = new SpeechRecognition(); }
    catch { setNotice(speechUnavailableNotice()); return; }
    let interim = '';
    let receivedResult = false;
    let hadError = false;
    const committed = new Set();
    recognition.lang = child.props.lang || 'mr-IN';
    recognition.continuous = false;
    recognition.interimResults = true;
    recognition.onstart = () => { setListening(true); setNotice('ऐकत आहे… मराठीत बोला.'); };
    recognition.onerror = voiceEvent => {
      hadError = true;
      setListening(false);
      setNotice(speechErrorNotice(voiceEvent.error));
    };
    recognition.onresult = voiceEvent => {
      for (let index = voiceEvent.resultIndex ?? 0; index < voiceEvent.results.length; index += 1) {
        const result = voiceEvent.results[index];
        const transcript = result?.[0]?.transcript?.trim() || '';
        if (result?.isFinal && !committed.has(index)) {
          committed.add(index);
          appendTranscript(transcript);
          receivedResult = true;
          interim = '';
        } else if (!result?.isFinal) {
          interim = transcript;
        }
      }
    };
    recognition.onend = () => {
      if (!hadError && !receivedResult && interim) {
        appendTranscript(interim);
        receivedResult = true;
      }
      if (!hadError) setNotice(receivedResult ? '' : speechErrorNotice('no-speech'));
      setListening(false);
      if (recognitionRef.current === recognition) recognitionRef.current = null;
    };
    recognitionRef.current = recognition;
    setListening(true);
    setNotice('मायक्रोफोन सुरू करत आहे…');
    try {
      // Keep start inside the tap handler so mobile browsers retain user activation.
      recognition.start();
    } catch {
      recognitionRef.current = null;
      setListening(false);
      setNotice('मायक्रोफोन सुरू करता आला नाही. पुन्हा टॅप करा किंवा कीबोर्डवरील माइक वापरा.');
    }
  }

  const multiline = child.type === 'textarea';
  const input = cloneElement(child, { onChange });
  const buttonLabel = listening ? 'बोलणे थांबवा' : supported ? 'मराठीत बोलून मजकूर भरा' : 'फोनच्या कीबोर्डवरील माइक वापरा';
  return <span ref={wrapperRef} className={`speech-input-wrap${multiline ? ' speech-input-multiline' : ''}`}>
    {input}
    <span className={`speech-input-control${listening ? ' listening' : ''}`}>
      <button type="button" className="speech-input-button" onClick={toggleListening} title={buttonLabel}
        aria-label={buttonLabel} aria-pressed={listening}>
        {listening ? <MicOff size={17}/> : <Mic size={17}/>}
      </button>
      {notice && <span className="speech-input-notice" role="status">{notice}</span>}
    </span>
  </span>;
}
