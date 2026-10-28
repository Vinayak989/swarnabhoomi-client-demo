export async function prepareProfilePhoto(file) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) throw new Error('JPG, PNG किंवा WEBP फोटो निवडा.');
  if (file.size > 10 * 1024 * 1024) throw new Error('फोटोचा मूळ आकार १० MB पेक्षा कमी असावा.');
  const source = URL.createObjectURL(file);
  try {
    const image = await new Promise((resolve, reject) => {
      const picture = new Image();
      picture.onload = () => resolve(picture);
      picture.onerror = () => reject(new Error('फोटो वाचता आला नाही. दुसरा फोटो निवडा.'));
      picture.src = source;
    });
    const side = Math.min(image.naturalWidth, image.naturalHeight);
    if (!side) throw new Error('फोटो वाचता आला नाही.');
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = Math.min(512, side);
    const context = canvas.getContext('2d');
    if (!context) throw new Error('फोटो तयार करता आला नाही.');
    context.fillStyle = '#f4f7ee'; context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(image, (image.naturalWidth - side) / 2, (image.naturalHeight - side) / 2, side, side, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.85));
    if (!blob || blob.size > 1024 * 1024) throw new Error('फोटो लहान करता आला नाही. दुसरा फोटो निवडा.');
    return new File([blob], 'profile.jpg', { type: 'image/jpeg' });
  } finally { URL.revokeObjectURL(source); }
}
