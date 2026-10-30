const MAX_CAMERA_PHOTO_BYTES = 40_000_000;
const unreadablePhoto = () => new Error('फोटो वाचता आला नाही. पुन्हा फोटो काढा किंवा गॅलरीतून JPG फोटो निवडा.');

function photoType(bytes) {
  const starts = (...values) => values.every((value, index) => bytes[index] === value);
  const text = (from, to) => String.fromCharCode(...bytes.slice(from, to));
  if (starts(0xff, 0xd8, 0xff)) return 'image/jpeg';
  if (starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return 'image/png';
  if (text(0, 4) === 'RIFF' && text(8, 12) === 'WEBP') return 'image/webp';
  if (['GIF87a', 'GIF89a'].includes(text(0, 6))) return 'image/gif';
  if (text(0, 2) === 'BM') return 'image/bmp';
  if (text(4, 8) === 'ftyp') {
    const brands = text(8, bytes.length);
    if (/avif|avis/.test(brands)) return 'image/avif';
    if (/heic|heix|hevc|hevx|mif1|msf1/.test(brands)) return 'image/heic';
  }
  throw new Error('कृपया फोटो फाईल निवडा (JPG, PNG किंवा WEBP).');
}

async function decodePhoto(blob) {
  if (typeof createImageBitmap === 'function') {
    try {
      const bitmap = await createImageBitmap(blob, { imageOrientation: 'from-image' });
      return { image: bitmap, width: bitmap.width, height: bitmap.height, release: () => bitmap.close() };
    } catch { /* Some mobile browsers need the image-element decoder instead. */ }
  }
  const source = URL.createObjectURL(blob);
  try {
    const image = await new Promise((resolve, reject) => {
      const picture = new Image();
      picture.onload = () => resolve(picture);
      picture.onerror = () => reject(unreadablePhoto());
      picture.src = source;
    });
    return { image, width: image.naturalWidth, height: image.naturalHeight, release: () => URL.revokeObjectURL(source) };
  } catch (error) {
    URL.revokeObjectURL(source);
    throw error;
  }
}

/** Snapshot camera files, detect their real format and send a small, correctly oriented JPEG. */
export async function prepareUploadPhoto(file) {
  if (!file?.size) throw unreadablePhoto();
  if (file.size > MAX_CAMERA_PHOTO_BYTES) throw new Error('४० MB पेक्षा लहान फोटो निवडा.');
  // Camera providers can omit the MIME type or return an octet-stream file.
  // Copy the bytes before releasing the file picker, rather than relying on its temporary URI.
  let bytes;
  try { bytes = new Uint8Array(await file.arrayBuffer()); }
  catch { throw unreadablePhoto(); }
  const type = photoType(bytes.subarray(0, 64));
  const decoded = await decodePhoto(new Blob([bytes], { type }));
  const canvas = document.createElement('canvas');
  try {
    if (!decoded.width || !decoded.height) throw unreadablePhoto();
    const scale = Math.min(1, 1920 / Math.max(decoded.width, decoded.height));
    canvas.width = Math.max(1, Math.round(decoded.width * scale));
    canvas.height = Math.max(1, Math.round(decoded.height * scale));
    const context = canvas.getContext('2d');
    if (!context) throw unreadablePhoto();
    context.fillStyle = '#fff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(decoded.image, 0, 0, canvas.width, canvas.height);
    for (const quality of [0.88, 0.72, 0.55]) {
      const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', quality));
      if (!blob) throw unreadablePhoto();
      if (blob.size <= 2_000_000) {
        const name = (String(file.name || 'camera-photo').replace(/\.[^.]+$/, '').replace(/[^\p{L}\p{N}_-]/gu, '-').slice(0, 80) || 'camera-photo') + '.jpg';
        return new File([blob], name, { type: 'image/jpeg', lastModified: file.lastModified || Date.now() });
      }
    }
    throw new Error('फोटो लहान करता आला नाही. कमी रिझोल्यूशनचा फोटो निवडा.');
  } finally {
    decoded.release();
    canvas.width = canvas.height = 0;
  }
}
