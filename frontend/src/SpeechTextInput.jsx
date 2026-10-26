import { cloneElement, isValidElement, useEffect, useRef, useState } from 'react';
import { Mic, MicOff } from 'lucide-react';
import './speechTextInput.css';

function canUseSpeechRecognition() {
  return typeof window !== 'undefined' && Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);
}

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
  const latestValue = useRef(String(child.props.value ?? child.props.defaultValue ?? ''));
  const supported = canUseSpeechRecognition();

  useEffect(() => {
    latestValue.current = String(child.props.value ?? child.props.defaultValue ?? '');
  }, [child.props.value, child.props.defaultValue]);

  useEffect(() => () => recognitionRef.current?.abort(), []);

  const onChange = event => {
    latestValue.current = event.target.value;
    child.props.onChange?.(event);
  };

  function toggleListening() {
    if (listening) {
      recognitionRef.current?.stop();
      return;
    }
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setNotice('या ब्राउझरमध्ये आवाजातून मजकूर भरणे उपलब्ध नाही.');
      return;
    }
    const recognition = new SpeechRecognition();
    recognition.lang = child.props.lang || 'mr-IN';
    recognition.continuous = true;
    recognition.interimResults = false;
    recognition.onstart = () => { setListening(true); setNotice(''); };
    recognition.onend = () => setListening(false);
    recognition.onerror = event => {
      setListening(false);
      if (event.error === 'not-allowed') setNotice('मायक्रोफोनची परवानगी द्या.');
      else if (event.error !== 'aborted') setNotice('आवाज ओळखता आला नाही; पुन्हा प्रयत्न करा.');
    };
    recognition.onresult = event => {
      let transcript = '';
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        if (event.results[index].isFinal) transcript += event.results[index][0].transcript;
      }
      transcript = transcript.trim();
      if (!transcript) return;
      const current = latestValue.current;
      const next = `${current}${current && !/\s$/.test(current) ? ' ' : ''}${transcript}`;
      latestValue.current = next;
      const target = { name: child.props.name, value: next };
      child.props.onChange?.({ target, currentTarget: target });
    };
    recognitionRef.current = recognition;
    try {
      recognition.start();
    } catch {
      setNotice('मायक्रोफोन सुरू करता आला नाही.');
    }
  }

  const multiline = child.type === 'textarea';
  const input = cloneElement(child, { onChange });
  const buttonLabel = !supported
    ? 'या ब्राउझरमध्ये आवाजातून मजकूर भरणे उपलब्ध नाही'
    : listening ? 'बोलणे थांबवा' : 'मराठीत बोलून मजकूर भरा';
  return <span className={`speech-input-wrap${multiline ? ' speech-input-multiline' : ''}`}>
    {input}
    <span className={`speech-input-control${listening ? ' listening' : ''}`}>
      <button type="button" className="speech-input-button" onClick={toggleListening} disabled={!supported} title={buttonLabel} aria-label={buttonLabel}>
        {listening ? <MicOff size={15}/> : <Mic size={15}/>}
      </button>
      {notice && <span className="speech-input-notice" role="status">{notice}</span>}
    </span>
  </span>;
}
