const PAGE_WIDTH = 1240;
const PAGE_HEIGHT = 1754;
const PDF_WIDTH = 595.28;
const PDF_HEIGHT = 841.89;
const MARGIN = 78;
const CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;
const CONTENT_BOTTOM = 1590;
const FONT = '"Noto Sans Devanagari", "Mangal", sans-serif';

function codedError(code, detail = '') {
  const error = new Error(detail ? `${code}: ${detail}` : code);
  error.code = code;
  return error;
}

const hasValue = value => value !== null && value !== undefined && !(typeof value === 'string' && !value.trim());
const money = value => `₹${Number(value || 0).toLocaleString('en-IN')}`;
const dateText = value => value
  ? new Date(`${value}T00:00:00`).toLocaleDateString('mr-IN', { day: 'numeric', month: 'short', year: 'numeric' })
  : '';
const entryCost = entry => Number(entry.laborCost || 0) + Number(entry.materialsCost || 0) + Number(entry.otherCost || 0);
const hasExpense = entry => ['laborCost', 'materialsCost', 'otherCost'].some(key => hasValue(entry[key]));
const weatherText = raw => {
  if (!raw) return '';
  try {
    const detail = typeof raw === 'string' ? JSON.parse(raw) : raw;
    return [detail.district, detail.state, detail.warning, detail.note].filter(Boolean).join(' · ');
  } catch {
    return String(raw);
  }
};

function reportFields(entry) {
  const expense = hasExpense(entry);
  const income = hasValue(entry.saleIncome);
  const amount = hasValue(entry.applicationAmount)
    ? `${entry.applicationAmount}${hasValue(entry.applicationUnit) ? ` ${entry.applicationUnit}` : ''}`
    : '';
  return [
    ['पीक / शेताचा विभाग', entry.farmSection],
    ['पीक', entry.cropName],
    ['शेताचा भाग', entry.plotName],
    ['हंगाम', entry.seasonName],
    ['क्षेत्र (एकर)', entry.plotAreaAcres],
    ['हवामान', entry.weather],
    ['पिनकोड', entry.pincode],
    ['हवामान माहिती', weatherText(entry.weatherDetails)],
    ['आज केलेली कामे', entry.workDone],
    ['वापरलेले साहित्य / खते', entry.materials],
    ['पाणी (लिटर)', entry.waterLitres],
    ['निविष्ठेचा प्रकार', entry.applicationKind],
    ['निविष्ठा', entry.applicationName],
    ['प्रमाण', amount],
    ['मजूर संख्या', entry.workerCount],
    ['मजुरी खर्च', hasValue(entry.laborCost) ? money(entry.laborCost) : ''],
    ['खते / साहित्य खर्च', hasValue(entry.materialsCost) ? money(entry.materialsCost) : ''],
    ['इतर खर्च', hasValue(entry.otherCost) ? money(entry.otherCost) : ''],
    ['एकूण खर्च', expense ? money(entryCost(entry)) : ''],
    ['उत्पादन', hasValue(entry.productionKg) ? `${Number(entry.productionKg).toLocaleString('mr-IN')} किलो` : ''],
    ['विक्री', hasValue(entry.soldKg) ? `${Number(entry.soldKg).toLocaleString('mr-IN')} किलो` : ''],
    ['विक्रीतून मिळालेले पैसे', income ? money(entry.saleIncome) : ''],
    ['निव्वळ उत्पन्न', income || expense ? money(Number(entry.saleIncome || 0) - entryCost(entry)) : ''],
    ['पिकाची सद्यस्थिती', entry.cropCondition],
    ['माती व सेंद्रिय शेती निरीक्षण', entry.soilNotes],
    ['कीड / रोग', entry.pestDisease],
    ['तीव्रता', entry.issueSeverity],
    ['केलेला उपाय', entry.intervention],
    ['पुढील पाहणी', hasValue(entry.followUpDate) ? dateText(entry.followUpDate) : ''],
    ['उद्याची नियोजित कामे', entry.tomorrowPlan],
    ['विशेष नोंद', entry.specialNote]
  ].filter(([, value]) => hasValue(value));
}

function wrappedLines(context, value, width) {
  const lines = [];
  for (const paragraph of String(value).split(/\r?\n/)) {
    if (!paragraph.trim()) {
      lines.push('');
      continue;
    }
    let line = '';
    for (const word of paragraph.trim().split(/\s+/)) {
      const candidate = line ? `${line} ${word}` : word;
      if (context.measureText(candidate).width <= width) {
        line = candidate;
        continue;
      }
      if (line) {
        lines.push(line);
        line = '';
      }
      // Split a long token so that URLs and notes cannot run off the page.
      for (const character of Array.from(word)) {
        if (context.measureText(line + character).width > width && line) {
          lines.push(line);
          line = '';
        }
        line += character;
      }
    }
    if (line) lines.push(line);
  }
  return lines.length ? lines : [''];
}

function canvasJpeg(canvas) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(async blob => {
      if (!blob) {
        reject(new Error('PDF_PAGE_ENCODING_FAILED'));
        return;
      }
      try {
        resolve(new Uint8Array(await blob.arrayBuffer()));
      } catch (error) {
        reject(error);
      }
    }, 'image/jpeg', 0.9);
  });
}

function imageFromBlob(blob) {
  return new Promise((resolve, reject) => {
    const source = URL.createObjectURL(blob);
    const image = new Image();
    image.onload = () => resolve({ image, source });
    image.onerror = () => {
      URL.revokeObjectURL(source);
      reject(codedError('PDF_PHOTO_DECODE_FAILED'));
    };
    image.src = source;
  });
}

function pdfFromJpegPages(pages) {
  const encoder = new TextEncoder();
  const encoded = value => encoder.encode(value);
  const objects = [];
  objects.push(encoded('1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n'));
  const children = pages.map((_, index) => `${3 + index * 3} 0 R`).join(' ');
  objects.push(encoded(`2 0 obj\n<< /Type /Pages /Kids [${children}] /Count ${pages.length} >>\nendobj\n`));
  pages.forEach((page, index) => {
    const pageId = 3 + index * 3;
    const imageId = pageId + 1;
    const contentId = pageId + 2;
    const content = encoded(`q\n${PDF_WIDTH} 0 0 ${PDF_HEIGHT} 0 0 cm\n/Im0 Do\nQ\n`);
    objects.push(encoded(`${pageId} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${PDF_WIDTH} ${PDF_HEIGHT}] /Resources << /XObject << /Im0 ${imageId} 0 R >> >> /Contents ${contentId} 0 R >>\nendobj\n`));
    objects.push(encoded(`${imageId} 0 obj\n<< /Type /XObject /Subtype /Image /Width ${PAGE_WIDTH} /Height ${PAGE_HEIGHT} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${page.length} >>\nstream\n`));
    objects.push(page);
    objects.push(encoded('\nendstream\nendobj\n'));
    objects.push(encoded(`${contentId} 0 obj\n<< /Length ${content.length} >>\nstream\n`));
    objects.push(content);
    objects.push(encoded('endstream\nendobj\n'));
  });

  const header = encoded('%PDF-1.4\n');
  const offsets = [];
  let position = header.length;
  let cursor = 0;
  offsets.push(position);
  position += objects[cursor++].length; // Catalog
  offsets.push(position);
  position += objects[cursor++].length; // Pages
  pages.forEach(() => {
    offsets.push(position);
    position += objects[cursor++].length; // Page
    offsets.push(position);
    position += objects[cursor++].length + objects[cursor++].length + objects[cursor++].length; // Image stream
    offsets.push(position);
    position += objects[cursor++].length + objects[cursor++].length + objects[cursor++].length; // Content stream
  });
  const xref = encoded(`xref\n0 ${offsets.length + 1}\n0000000000 65535 f \n${offsets.map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size ${offsets.length + 1} /Root 1 0 R >>\nstartxref\n${position}\n%%EOF`);
  return new Blob([header, ...objects, xref], { type: 'application/pdf' });
}

/** Create a direct-download PDF; each attached photo is loaded before the file is returned. */
export async function createDailyEntryPdf(entries, { photoForEntry, loadPhoto }) {
  if (!Array.isArray(entries) || !entries.length) throw new Error('PDF_NO_ENTRIES');
  if (typeof photoForEntry !== 'function' || typeof loadPhoto !== 'function') throw new Error('PDF_PHOTO_LOADER_REQUIRED');

  // Canvas must use a loaded Devanagari font, otherwise Marathi text can be blank in the PDF.
  if (document.fonts?.load) {
    await Promise.race([
      Promise.all([document.fonts.load(`24px ${FONT}`), document.fonts.load(`bold 24px ${FONT}`)]).catch(() => {}),
      new Promise(resolve => setTimeout(resolve, 5000))
    ]);
  }

  const pages = [];
  let canvas;
  let context;
  let pageNumber = 0;
  let y = 0;

  function startPage() {
    pageNumber += 1;
    canvas = document.createElement('canvas');
    canvas.width = PAGE_WIDTH;
    canvas.height = PAGE_HEIGHT;
    context = canvas.getContext('2d');
    if (!context) throw new Error('PDF_CANVAS_UNAVAILABLE');
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
    context.fillStyle = '#114c35';
    context.fillRect(0, 0, PAGE_WIDTH, 142);
    context.fillStyle = '#ffffff';
    context.font = `700 39px ${FONT}`;
    context.fillText('स्वर्णभूमी', MARGIN, 67);
    context.font = `24px ${FONT}`;
    context.fillText('दैनिक शेती नोंदवही', MARGIN, 111);
    context.fillStyle = '#def5e6';
    context.font = `19px ${FONT}`;
    context.textAlign = 'right';
    context.fillText(new Date().toLocaleDateString('mr-IN'), PAGE_WIDTH - MARGIN, 94);
    context.textAlign = 'left';
    y = 181;
  }

  async function finishPage() {
    context.strokeStyle = '#d7e2da';
    context.lineWidth = 2;
    context.beginPath();
    context.moveTo(MARGIN, 1648);
    context.lineTo(PAGE_WIDTH - MARGIN, 1648);
    context.stroke();
    context.fillStyle = '#647568';
    context.font = `18px ${FONT}`;
    context.fillText('स्वर्णभूमी · तुमच्या कष्टांची नोंद', MARGIN, 1690);
    context.textAlign = 'right';
    context.fillText(`पान ${pageNumber.toLocaleString('mr-IN')}`, PAGE_WIDTH - MARGIN, 1690);
    context.textAlign = 'left';
    pages.push(await canvasJpeg(canvas));
    canvas.width = 0;
    canvas.height = 0;
  }

  async function ensureSpace(height) {
    if (y + height > CONTENT_BOTTOM) {
      await finishPage();
      startPage();
    }
  }

  async function drawField(label, value) {
    if (!hasValue(value)) return;
    context.font = `25px ${FONT}`;
    const lines = wrappedLines(context, value, CONTENT_WIDTH - 36);
    let index = 0;
    do {
      await ensureSpace(85);
      context.fillStyle = '#edf5ef';
      context.fillRect(MARGIN, y, CONTENT_WIDTH, 42);
      context.fillStyle = '#316147';
      context.font = `700 20px ${FONT}`;
      context.fillText(index ? `${label} (पुढे)` : label, MARGIN + 14, y + 28);
      y += 53;
      context.font = `25px ${FONT}`;
      context.fillStyle = '#1a2d22';
      while (index < lines.length && y + 35 <= CONTENT_BOTTOM) {
        context.fillText(lines[index], MARGIN + 15, y + 23);
        y += 36;
        index += 1;
      }
      y += 12;
      if (index < lines.length) {
        await finishPage();
        startPage();
      }
    } while (index < lines.length);
    y += 5;
  }

  async function drawEntryHeading(entry, index) {
    await ensureSpace(106);
    context.fillStyle = '#dcefe2';
    context.fillRect(MARGIN, y, CONTENT_WIDTH, 74);
    context.fillStyle = '#114c35';
    context.font = `700 27px ${FONT}`;
    context.fillText(`नोंद ${(index + 1).toLocaleString('mr-IN')}`, MARGIN + 20, y + 47);
    context.textAlign = 'right';
    context.font = `24px ${FONT}`;
    context.fillText(dateText(entry.entryDate) || 'दिनांक नाही', PAGE_WIDTH - MARGIN - 20, y + 47);
    context.textAlign = 'left';
    y += 95;
  }

  async function drawPhoto(photo) {
    let blob;
    try {
      blob = await loadPhoto(photo.url);
    } catch (error) {
      throw codedError('PDF_PHOTO_UNAVAILABLE', photo.label);
    }
    if (!(blob instanceof Blob) || !blob.size || !blob.type.toLowerCase().startsWith('image/')) {
      throw codedError('PDF_PHOTO_UNAVAILABLE', photo.label);
    }
    const { image, source } = await imageFromBlob(blob);
    try {
      if (!image.naturalWidth || !image.naturalHeight) throw codedError('PDF_PHOTO_DECODE_FAILED', photo.label);
      const imageWidth = CONTENT_WIDTH - 34;
      const imageHeight = Math.min(940, imageWidth * image.naturalHeight / image.naturalWidth);
      const displayWidth = imageHeight * image.naturalWidth / image.naturalHeight;
      const cardHeight = imageHeight + 90;
      await ensureSpace(cardHeight);
      context.fillStyle = '#edf5ef';
      context.fillRect(MARGIN, y, CONTENT_WIDTH, 42);
      context.fillStyle = '#316147';
      context.font = `700 20px ${FONT}`;
      context.fillText(photo.label, MARGIN + 14, y + 28);
      y += 55;
      const imageX = MARGIN + (CONTENT_WIDTH - displayWidth) / 2;
      context.drawImage(image, imageX, y, displayWidth, imageHeight);
      context.strokeStyle = '#d7e2da';
      context.lineWidth = 2;
      context.strokeRect(imageX, y, displayWidth, imageHeight);
      y += imageHeight + 32;
    } finally {
      URL.revokeObjectURL(source);
    }
  }

  startPage();
  const showExpense = entries.some(hasExpense);
  const showIncome = entries.some(entry => hasValue(entry.saleIncome));
  const summary = [
    `एकूण नोंदी: ${entries.length.toLocaleString('mr-IN')}`,
    ...(showExpense ? [`एकूण खर्च: ${money(entries.reduce((sum, entry) => sum + entryCost(entry), 0))}`] : []),
    ...(showIncome ? [`एकूण उत्पन्न: ${money(entries.reduce((sum, entry) => sum + Number(entry.saleIncome || 0), 0))}`] : []),
    ...(showExpense || showIncome ? [`निव्वळ उत्पन्न: ${money(entries.reduce((sum, entry) => sum + Number(entry.saleIncome || 0) - entryCost(entry), 0))}`] : [])
  ];
  for (const item of summary) {
    await ensureSpace(42);
    context.fillStyle = '#25583b';
    context.font = `22px ${FONT}`;
    context.fillText(item, MARGIN, y + 25);
    y += 43;
  }
  y += 20;

  for (let index = 0; index < entries.length; index += 1) {
    const entry = entries[index];
    await drawEntryHeading(entry, index);
    for (const [label, value] of reportFields(entry)) await drawField(label, value);
    for (const photo of photoForEntry(entry) || []) await drawPhoto(photo);
    y += 20;
  }
  await finishPage();
  return pdfFromJpegPages(pages);
}
