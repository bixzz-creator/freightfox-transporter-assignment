/**
 * FREIGHTFOX ENTERPRISE PLATFORM — DATASET PARSER
 * Parses Matrix Grid tender CSVs and Flat CSVs with client-side validation
 */

'use strict';

const DEFAULT_INDIAN_CITY_PAIRS = [
  { origin: "Mumbai", destination: "Delhi" },
  { origin: "Delhi", destination: "Bangalore" },
  { origin: "Chennai", destination: "Kolkata" },
  { origin: "Pune", destination: "Hyderabad" },
  { origin: "Ahmedabad", destination: "Jaipur" },
  { origin: "Guwahati", destination: "Shillong" },
  { origin: "Lucknow", destination: "Patna" },
  { origin: "Ranchi", destination: "Bhubaneswar" },
  { origin: "Surat", destination: "Indore" },
  { origin: "Nagpur", destination: "Raipur" },
  { origin: "Chandigarh", destination: "Shimla" },
  { origin: "Coimbatore", destination: "Kochi" },
  { origin: "Visakhapatnam", destination: "Vijayawada" },
  { origin: "Bhopal", destination: "Jabalpur" }
];

const datasetParser = {

  parse(csvText, fileName = 'uploaded.csv') {
    if (!csvText || typeof csvText !== 'string') {
      throw new Error('CSV content is empty.');
    }

    const lines = csvText.trim().split(/\r?\n/).map(l => l.trim()).filter(l => l.length > 0);
    if (lines.length < 2) {
      throw new Error('CSV file is empty or contains only headers.');
    }

    const rawHeaders = lines[0].split(',').map(h => h.trim().replace(/^["']|["']$/g, ''));
    const lowerHeaders = rawHeaders.map(h => h.toLowerCase());

    // Detect Matrix format: Row 0 has carrier columns, Row headers are lanes
    const isMatrix = (
      lowerHeaders[0].includes('lane') || lowerHeaders[0].includes('trade') || lowerHeaders[0].includes('route')
    ) && rawHeaders.length >= 2 && !lowerHeaders.includes('quote') && !lowerHeaders.includes('price');

    if (isMatrix) {
      return this.parseMatrix(lines, rawHeaders, fileName);
    } else {
      return this.parseFlat(lines, rawHeaders, lowerHeaders, fileName);
    }
  },

  parseMatrix(lines, headers, fileName) {
    const transporterCols = [];
    const transporterMap = new Map();

    for (let col = 1; col < headers.length; col++) {
      const headerName = headers[col].trim();
      if (!headerName) continue;

      const numMatch = headerName.match(/\d+/);
      const tId = numMatch ? parseInt(numMatch[0]) : col;
      const tName = headerName.startsWith('T') || headerName.toLowerCase().includes('transporter')
        ? headerName
        : `Transporter ${headerName}`;

      transporterCols.push({ colIndex: col, id: tId, name: tName });
      transporterMap.set(tId, { id: tId, name: tName, laneQuotes: [] });
    }

    if (transporterCols.length === 0) {
      throw new Error('No transporter columns found in the matrix CSV header.');
    }

    const laneMap = new Map();
    const quotesPreview = [];
    let minQuote = Infinity;
    let maxQuote = -Infinity;
    let totalQuoteSum = 0;
    let quoteCount = 0;

    for (let row = 1; row < lines.length; row++) {
      const cells = lines[row].split(',').map(c => c.trim().replace(/^["']|["']$/g, ''));
      if (cells.length < 2) continue;

      const laneCell = cells[0];
      const numMatch = laneCell.match(/\d+/);
      const laneId = numMatch ? parseInt(numMatch[0]) : row;

      let origin = `Origin ${laneId}`;
      let destination = `Destination ${laneId}`;
      if (laneId >= 1 && laneId <= DEFAULT_INDIAN_CITY_PAIRS.length) {
        origin = DEFAULT_INDIAN_CITY_PAIRS[laneId - 1].origin;
        destination = DEFAULT_INDIAN_CITY_PAIRS[laneId - 1].destination;
      }

      if (!laneMap.has(laneId)) {
        laneMap.set(laneId, { id: laneId, origin, destination });
      }

      for (const t of transporterCols) {
        if (t.colIndex < cells.length) {
          const rawVal = cells[t.colIndex];
          const quoteVal = parseFloat(rawVal);
          if (!isNaN(quoteVal) && quoteVal > 0) {
            transporterMap.get(t.id).laneQuotes.push({ laneId, quote: quoteVal });
            quotesPreview.push({
              laneId,
              origin,
              destination,
              transporterId: t.id,
              transporterName: t.name,
              quote: quoteVal
            });
            minQuote = Math.min(minQuote, quoteVal);
            maxQuote = Math.max(maxQuote, quoteVal);
            totalQuoteSum += quoteVal;
            quoteCount++;
          }
        }
      }
    }

    if (laneMap.size === 0 || quoteCount === 0) {
      throw new Error('No valid positive quotations found in the matrix CSV.');
    }

    const lanes = Array.from(laneMap.values());
    const transporters = Array.from(transporterMap.values()).filter(t => t.laneQuotes.length > 0);

    return {
      fileName,
      format: 'Matrix Grid Tender',
      lanes,
      transporters,
      quotesPreview,
      stats: {
        laneCount: lanes.length,
        transporterCount: transporters.length,
        quoteCount,
        minQuote: minQuote === Infinity ? 0 : minQuote,
        maxQuote: maxQuote === -Infinity ? 0 : maxQuote,
        avgQuote: quoteCount > 0 ? (totalQuoteSum / quoteCount) : 0
      }
    };
  },

  parseFlat(lines, rawHeaders, lowerHeaders, fileName) {
    const laneIdIdx = lowerHeaders.findIndex(h => h.includes('lane') && h.includes('id'));
    const originIdx = lowerHeaders.findIndex(h => h.includes('origin') || h.includes('from') || h.includes('source'));
    const destIdx = lowerHeaders.findIndex(h => h.includes('dest') || h.includes('to'));
    const transIdIdx = lowerHeaders.findIndex(h => (h.includes('transporter') || h.includes('carrier') || h.includes('vendor')) && h.includes('id'));
    const transNameIdx = lowerHeaders.findIndex(h => (h.includes('transporter') || h.includes('carrier') || h.includes('vendor')) && (h.includes('name') || !h.includes('id')));
    const quoteIdx = lowerHeaders.findIndex(h => h.includes('quote') || h.includes('price') || h.includes('cost') || h.includes('amount') || h.includes('rate'));

    if (laneIdIdx === -1 || originIdx === -1 || destIdx === -1 || quoteIdx === -1) {
      throw new Error('Flat CSV must contain headers for Lane ID, Origin, Destination, Transporter ID/Name, and Quote.');
    }

    const laneMap = new Map();
    const transporterMap = new Map();
    const quotesPreview = [];
    let minQuote = Infinity;
    let maxQuote = -Infinity;
    let totalQuoteSum = 0;
    let quoteCount = 0;

    for (let i = 1; i < lines.length; i++) {
      const cols = lines[i].split(',').map(c => c.trim().replace(/^["']|["']$/g, ''));
      if (cols.length < 4) continue;

      const laneId = parseInt(cols[laneIdIdx]);
      const origin = cols[originIdx];
      const destination = cols[destIdx];
      const tId = transIdIdx !== -1 ? parseInt(cols[transIdIdx]) : (i % 5 + 1);
      const tName = transNameIdx !== -1 ? cols[transNameIdx] : `Transporter ${tId}`;
      const quote = parseFloat(cols[quoteIdx]);

      if (isNaN(laneId) || !origin || !destination || isNaN(quote) || quote <= 0) {
        continue;
      }

      if (!laneMap.has(laneId)) {
        laneMap.set(laneId, { id: laneId, origin, destination });
      }

      if (!transporterMap.has(tId)) {
        transporterMap.set(tId, { id: tId, name: tName, laneQuotes: [] });
      }

      transporterMap.get(tId).laneQuotes.push({ laneId, quote });
      quotesPreview.push({
        laneId,
        origin,
        destination,
        transporterId: tId,
        transporterName: tName,
        quote
      });

      minQuote = Math.min(minQuote, quote);
      maxQuote = Math.max(maxQuote, quote);
      totalQuoteSum += quote;
      quoteCount++;
    }

    if (laneMap.size === 0 || quoteCount === 0) {
      throw new Error('No valid positive quotations could be parsed from the flat CSV.');
    }

    const lanes = Array.from(laneMap.values());
    const transporters = Array.from(transporterMap.values());

    return {
      fileName,
      format: 'Flat Table',
      lanes,
      transporters,
      quotesPreview,
      stats: {
        laneCount: lanes.length,
        transporterCount: transporters.length,
        quoteCount,
        minQuote: minQuote === Infinity ? 0 : minQuote,
        maxQuote: maxQuote === -Infinity ? 0 : maxQuote,
        avgQuote: quoteCount > 0 ? (totalQuoteSum / quoteCount) : 0
      }
    };
  },

  getTemplateCsv() {
    return `Trade Lane,T1,T2,T3,T4,T5,T6,T7
Lane 1,20835,48844,39020,14400,11601,35095,26070
Lane 2,10512,31326,20648,44514,19760,12494,41098
Lane 3,22105,18640,31438,14316,40870,17808,20932`;
  },

  getSampleCsv() {
    return `Trade Lane,T1,T2,T3,T4,T5,T6,T7
Lane 1,20835,48844,39020,14400,11601,35095,26070
Lane 2,10512,31326,20648,44514,19760,12494,41098
Lane 3,22105,18640,31438,14316,40870,17808,20932
Lane 4,42481,45828,36447,10678,20635,36210,16897
Lane 5,19862,18297,12789,13032,26421,39444,27938
Lane 6,13567,45810,49985,46024,28809,29948,43517
Lane 7,10015,49573,35285,42342,27815,17320,25881
Lane 8,17886,45122,10281,35742,17024,10492,46136
Lane 9,43996,31856,40092,48921,44691,37864,31286`;
  }
};
