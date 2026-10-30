import { prepareUploadPhoto } from '../src/photoUpload.js';

const check = (condition, message) => { if (!condition) throw new Error(message); };
const jpeg = canvas => new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.95));

// Run in a real browser: these regressions depend on image decoding, EXIF and canvas encoding.
// With the Vite server running: await (await import('/tests/photoUpload.browser.js')).runPhotoUploadBrowserChecks()
export async function runPhotoUploadBrowserChecks() {
  const canvas = document.createElement('canvas');
  canvas.width = 3200; canvas.height = 1800;
  const context = canvas.getContext('2d');
  context.fillStyle = '#1d7335'; context.fillRect(0, 0, canvas.width, canvas.height);
  const largeJpeg = await jpeg(canvas);
  const camera = new File([largeJpeg, new Uint8Array(11_000_000)], 'camera-capture', { type: '' });
  const prepared = await prepareUploadPhoto(camera);
  const bitmap = await createImageBitmap(prepared);
  check(camera.size > 10_000_000, 'Fixture must exceed the old camera limit');
  check(prepared.type === 'image/jpeg' && prepared.name.endsWith('.jpg'), 'Camera output must have a JPEG type and name');
  check(prepared.size < 2_000_000 && bitmap.width === 1920 && bitmap.height === 1080, 'Camera photo must fit upload limits without distortion');
  bitmap.close();

  const octetStream = await prepareUploadPhoto(new File([largeJpeg], 'capture.bin', { type: 'application/octet-stream' }));
  check(octetStream.type === 'image/jpeg', 'Camera provider MIME must not reject a valid image');
  const gallery = await prepareUploadPhoto(new File([largeJpeg], 'gallery.jpg', { type: 'image/jpeg' }));
  check(gallery.size > 0, 'Gallery selection must continue working');

  canvas.width = 120; canvas.height = 80;
  context.fillStyle = '#ff0000'; context.fillRect(0, 0, 60, 80);
  context.fillStyle = '#0000ff'; context.fillRect(60, 0, 60, 80);
  const original = new Uint8Array(await (await jpeg(canvas)).arrayBuffer());
  // APP1: Exif, little-endian TIFF, orientation 6 (portrait, rotate 90 degrees clockwise).
  const exif = new Uint8Array([0xff,0xe1,0,34,69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0]);
  const portraitFile = new File([original.slice(0,2), exif, original.slice(2)], 'portrait.jpg', { type: '' });
  const portrait = await prepareUploadPhoto(portraitFile);
  const portraitBitmap = await createImageBitmap(portrait);
  check(portraitBitmap.width === 80 && portraitBitmap.height === 120, 'Camera EXIF orientation must be preserved in the JPEG pixels');
  portraitBitmap.close();

  const nativeBitmap = window.createImageBitmap;
  try {
    window.createImageBitmap = undefined;
    const fallback = await prepareUploadPhoto(portraitFile);
    const image = await nativeBitmap(fallback);
    check(image.width === 80 && image.height === 120, 'Mobile image-element fallback must keep portrait orientation');
    image.close();
  } finally { window.createImageBitmap = nativeBitmap; }

  for (const invalid of [new File(['not an image'], 'fake.jpg', {type:'image/jpeg'}), new File([], 'empty.jpg'), new File([new Uint8Array(40_000_001)], 'too-big.jpg')]) {
    let rejected = false;
    try { await prepareUploadPhoto(invalid); } catch { rejected = true; }
    check(rejected, 'Invalid and oversized camera files must show an error');
  }
  canvas.width = canvas.height = 0;
  return { passed: true, largeCamera: true, missingMime: true, octetStream: true, gallery: true, portraitExif: true, mobileDecoderFallback: true, invalidFilesRejected: true };
}
