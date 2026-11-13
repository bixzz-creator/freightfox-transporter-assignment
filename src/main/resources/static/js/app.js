/**
 * FREIGHTFOX ENTERPRISE PLATFORM — APPLICATION CONTROLLER
 * Milestone 3: Real Dataset Management, Overview Dashboard & Backend API Integration
 */

'use strict';

// 1. Central Application State Store
window.AppState = {
  currentView: 'overview',
  dataset: null,              // Holds real uploaded tender dataset
  optimizationResult: null,   // Holds real optimization result from backend
  isHealthy: false,
  pendingParsedDataset: null  // Parsed before backend persistence
};

// 2. View Metadata Registry
const VIEW_METADATA = {
  overview: {
    title: 'Overview Dashboard',
    breadcrumb: 'Overview',
    description: 'Executive procurement metrics, transporter coverage, and assignment performance summary.'
  },
  optimization: {
    title: 'Lane Optimization',
    breadcrumb: 'Lane Optimization',
    description: 'Bitmask Dynamic Programming execution engine and operational constraints configuration.'
  },
  map: {
    title: 'Live Transportation Map',
    breadcrumb: 'Live Map',
    description: 'Interactive multi-lane GIS projection with distinct route corridors, highway routing, and vehicle markers.'
  },
  assignments: {
    title: 'Lane Allocation Registry',
    breadcrumb: 'Assignments',
    description: 'Granular view of all assigned transportation lanes, winning carriers, and bid quotations.'
  },
  dataset: {
    title: 'Tender Dataset Management',
    breadcrumb: 'Dataset Management',
    description: 'Upload and atomically persist freight rates in Matrix Grid or Flat CSV format.'
  },
  reports: {
    title: 'Procurement Reports & Analytics',
    breadcrumb: 'Reports',
    description: 'Exportable procurement summaries, carrier allocation breakdown, and expenditure analysis.'
  }
};

// 3. Application Router
const appRouter = {
  init() {
    window.addEventListener('hashchange', () => this.handleHashChange());
    this.handleHashChange();
  },

  navigate(viewName) {
    if (!VIEW_METADATA[viewName]) {
      viewName = 'overview';
    }
    window.location.hash = viewName;
  },

  handleHashChange() {
    let hash = window.location.hash.replace('#', '').trim();
    if (!hash || !VIEW_METADATA[hash]) {
      hash = 'overview';
    }

    window.AppState.currentView = hash;
    this.renderView(hash);
  },

  renderView(viewName) {
    const meta = VIEW_METADATA[viewName] || VIEW_METADATA.overview;

    // A. Update Active Navigation Links in Sidebar
    document.querySelectorAll('.nav-link').forEach(link => {
      const targetView = link.getAttribute('data-view');
      if (targetView === viewName) {
        link.classList.add('active');
      } else {
        link.classList.remove('active');
      }
    });

    // B. Switch Active View Panel
    document.querySelectorAll('.view-panel').forEach(panel => {
      if (panel.id === `view-${viewName}`) {
        panel.classList.add('active');
      } else {
        panel.classList.remove('active');
      }
    });

    // C. Update Header Typography & Breadcrumb
    const breadcrumbEl = document.getElementById('breadcrumbActive');
    const headingTitleEl = document.getElementById('viewHeadingTitle');
    const headingDescEl = document.getElementById('viewHeadingDesc');

    if (breadcrumbEl) breadcrumbEl.textContent = meta.breadcrumb;
    if (headingTitleEl) {
      const pillHtml = window.AppState.dataset
        ? `<span class="status-pill success" id="activeDatasetPill">Active Dataset: ${window.AppState.dataset.lanes.length} Lanes</span>`
        : `<span class="status-pill info" id="activeDatasetPill">No Dataset Loaded</span>`;
      headingTitleEl.innerHTML = `${meta.title} ${pillHtml}`;
    }
    if (headingDescEl) headingDescEl.textContent = meta.description;

    // D. Update Page Document Title
    document.title = `FreightFox — ${meta.title}`;

    // E. Close Mobile Sidebar if Open
    sidebarController.close();

    // F. Notify specific view listeners
    window.dispatchEvent(new CustomEvent('viewChanged', { detail: { viewName } }));
  }
};

// 4. Responsive Sidebar Controller (Mobile / Tablet Drawer)
const sidebarController = {
  sidebar: null,
  backdrop: null,
  toggleBtn: null,

  init() {
    this.sidebar = document.getElementById('appSidebar');
    this.backdrop = document.getElementById('sidebarBackdrop');
    this.toggleBtn = document.getElementById('sidebarToggleBtn');

    if (this.toggleBtn) {
      this.toggleBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this.toggle();
      });
    }

    if (this.backdrop) {
      this.backdrop.addEventListener('click', () => {
        this.close();
      });
    }

    // Close when clicking any nav link on small screens
    document.querySelectorAll('.nav-link').forEach(link => {
      link.addEventListener('click', () => {
        if (window.innerWidth <= 768) {
          this.close();
        }
      });
    });
  },

  toggle() {
    if (!this.sidebar) return;
    const isOpen = this.sidebar.classList.contains('sidebar-open');
    if (isOpen) {
      this.close();
    } else {
      this.open();
    }
  },

  open() {
    if (this.sidebar) this.sidebar.classList.add('sidebar-open');
    if (this.backdrop) this.backdrop.classList.add('active');
    document.body.style.overflow = 'hidden';
  },

  close() {
    if (this.sidebar) this.sidebar.classList.remove('sidebar-open');
    if (this.backdrop) this.backdrop.classList.remove('active');
    document.body.style.overflow = '';
  }
};

// 5. System Health Poller (Actuator Integration)
const healthPoller = {
  pill: null,
  text: null,
  dot: null,
  dbVal: null,

  init() {
    this.pill = document.getElementById('healthPill');
    this.text = document.getElementById('healthText');
    this.dot = document.getElementById('healthDot');
    this.dbVal = document.getElementById('dbModeVal');

    this.checkHealth();
    // Poll every 30 seconds
    setInterval(() => this.checkHealth(), 30000);
  },

  async checkHealth() {
    try {
      const res = await fetch('/actuator/health');
      if (res.ok) {
        const data = await res.json();
        if (data.status === 'UP') {
          window.AppState.isHealthy = true;
          if (this.text) this.text.textContent = 'System Online';
          if (this.dot) {
            this.dot.style.backgroundColor = '#10b981';
            this.dot.style.boxShadow = '0 0 8px #10b981';
          }
          if (this.dbVal && data.components && data.components.db) {
            this.dbVal.textContent = data.components.db.details?.database || 'PostgreSQL / H2';
          }
          return;
        }
      }
      this.setDegraded();
    } catch (err) {
      this.setDegraded();
    }
  },

  setDegraded() {
    window.AppState.isHealthy = false;
    if (this.text) this.text.textContent = 'Backend Reconnecting';
    if (this.dot) {
      this.dot.style.backgroundColor = '#f59e0b';
      this.dot.style.boxShadow = '0 0 8px #f59e0b';
    }
  }
};

// 6. Dataset Manager Controller (Upload, Validation, Persistence)
const datasetManager = {
  dropZone: null,
  fileInput: null,
  btnSelectCsv: null,
  btnDownloadTemplate: null,
  btnLoadSampleDataset: null,
  validationBanner: null,
  previewCard: null,
  btnPersistDataset: null,
  successBanner: null,

  init() {
    this.dropZone = document.getElementById('dropZone');
    this.fileInput = document.getElementById('csvFileInput');
    this.btnSelectCsv = document.getElementById('btnSelectCsv');
    this.btnDownloadTemplate = document.getElementById('btnDownloadTemplate');
    this.btnLoadSampleDataset = document.getElementById('btnLoadSampleDataset');
    this.validationBanner = document.getElementById('datasetValidationBanner');
    this.previewCard = document.getElementById('datasetPreviewCard');
    this.btnPersistDataset = document.getElementById('btnPersistDataset');
    this.successBanner = document.getElementById('datasetSuccessBanner');

    // Clicking "Select CSV File" triggers native file picker
    if (this.btnSelectCsv && this.fileInput) {
      this.btnSelectCsv.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.fileInput.value = ''; // Reset so selecting the same file triggers change
        this.fileInput.click();
      });
    }

    // Clicking drop zone triggers file input ONLY if user didn't click an inner button
    if (this.dropZone && this.fileInput) {
      this.dropZone.addEventListener('click', (e) => {
        if (e.target.closest('button') || e.target.closest('a')) {
          return;
        }
        this.fileInput.value = '';
        this.fileInput.click();
      });

      // Drag and drop listeners
      this.dropZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.dropZone.classList.add('dragover');
      });
      this.dropZone.addEventListener('dragleave', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.dropZone.classList.remove('dragover');
      });
      this.dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.dropZone.classList.remove('dragover');
        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
          this.handleFile(e.dataTransfer.files[0]);
        }
      });
    }

    // File input change
    if (this.fileInput) {
      this.fileInput.addEventListener('change', (e) => {
        if (e.target.files && e.target.files.length > 0) {
          this.handleFile(e.target.files[0]);
        }
      });
    }

    // Download template
    if (this.btnDownloadTemplate) {
      this.btnDownloadTemplate.addEventListener('click', (e) => {
        e.stopPropagation();
        this.downloadTemplate();
      });
    }

    // Load sample dataset
    if (this.btnLoadSampleDataset) {
      this.btnLoadSampleDataset.addEventListener('click', (e) => {
        e.stopPropagation();
        this.loadSample();
      });
    }

    // Persist to backend button
    if (this.btnPersistDataset) {
      this.btnPersistDataset.addEventListener('click', () => {
        this.persistToBackend();
      });
    }
  },

  handleFile(file) {
    if (!file.name.toLowerCase().endsWith('.csv')) {
      this.showValidation(`Invalid file type "${file.name}". Please upload a valid .csv file.`, 'error');
      return;
    }

    const reader = new FileReader();
    reader.onload = (e) => {
      this.processCsvContent(e.target.result, file.name);
    };
    reader.onerror = () => {
      this.showValidation(`Failed to read file "${file.name}".`, 'error');
    };
    reader.readAsText(file);
  },

  processCsvContent(csvText, fileName) {
    try {
      const parsed = datasetParser.parse(csvText, fileName);
      window.AppState.pendingParsedDataset = parsed;

      this.showValidation(
        `✓ Parsed "${fileName}" (${parsed.format}): ${parsed.stats.laneCount} lanes, ${parsed.stats.transporterCount} transporters, ${parsed.stats.quoteCount} quotations.`,
        'success'
      );

      this.renderPreview(parsed);

      // Automatically persist to backend
      this.persistToBackend();

    } catch (err) {
      this.showValidation(`Error parsing CSV: ${err.message}`, 'error');
      if (this.previewCard) this.previewCard.style.display = 'none';
      if (this.successBanner) this.successBanner.style.display = 'none';
    }
  },

  renderPreview(parsed) {
    if (!this.previewCard) return;

    this.previewCard.style.display = 'block';
    if (this.successBanner) this.successBanner.style.display = 'none';

    document.getElementById('previewLaneCount').textContent = parsed.stats.laneCount;
    document.getElementById('previewTransporterCount').textContent = parsed.stats.transporterCount;
    document.getElementById('previewQuoteCount').textContent = parsed.stats.quoteCount;
    document.getElementById('previewAvgQuote').textContent = '₹' + Number(parsed.stats.avgQuote).toLocaleString('en-IN', { minimumFractionDigits: 2 });

    const tbody = document.getElementById('datasetPreviewTableBody');
    if (tbody) {
      tbody.innerHTML = '';
      // Show first 10 quotes in preview
      parsed.quotesPreview.slice(0, 10).forEach(q => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td><b>#${q.laneId}</b></td>
          <td>${q.origin} <span class="route-arrow">→</span> ${q.destination}</td>
          <td><b>${q.transporterName}</b> <small style="color:var(--text-muted);">(ID: ${q.transporterId})</small></td>
          <td><span class="quote-badge">₹${Number(q.quote).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span></td>
        `;
        tbody.appendChild(tr);
      });
    }

    this.previewCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  },

  async persistToBackend() {
    const parsed = window.AppState.pendingParsedDataset;
    if (!parsed) return;

    const btn = this.btnPersistDataset;
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = `<span>Saving to Database...</span>`;
    }

    try {
      const payload = {
        lanes: parsed.lanes,
        transporters: parsed.transporters
      };

      const res = await fetch('/api/v1/transporters/input', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.message || `HTTP ${res.status}: Failed to persist dataset.`);
      }

      const result = await res.json();

      // Update state
      window.AppState.dataset = parsed;
      window.AppState.optimizationResult = null; // Reset prior optimization

      // Show success banner
      if (this.successBanner) {
        this.successBanner.style.display = 'flex';
        document.getElementById('datasetSuccessMessage').textContent =
          `Atomic replacement successful: ${result.lanesCount} lanes, ${result.transportersCount} transporters, ${result.quotesCount} quotations persisted in database.`;
      }

      // Update Header active status pill
      const activePill = document.getElementById('activeDatasetPill');
      if (activePill) {
        activePill.className = 'status-pill success';
        activePill.textContent = `Active Dataset: ${parsed.stats.laneCount} Lanes`;
      }

      // Synchronize Overview Dashboard
      overviewManager.update(parsed);

      this.showValidation(
        `✓ Dataset successfully saved and persisted atomically! Ready for optimization.`,
        'success'
      );

    } catch (err) {
      this.showValidation(`Database Persistence Error: ${err.message}`, 'error');
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.innerHTML = `
          <svg width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/>
          </svg>
          <span>Save & Persist to Database</span>
        `;
      }
    }
  },

  showValidation(message, type) {
    if (!this.validationBanner) return;
    this.validationBanner.style.display = 'block';
    this.validationBanner.className = `validation-banner ${type}`;
    this.validationBanner.textContent = message;
  },

  downloadTemplate() {
    const template = datasetParser.getTemplateCsv();
    const blob = new Blob([template], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'freightfox_transporter_matrix_template.csv';
    a.click();
    window.URL.revokeObjectURL(url);
  },

  loadSample() {
    const sampleCsv = datasetParser.getSampleCsv();
    this.processCsvContent(sampleCsv, 'freightfox_sample_tender.csv');
  }
};

// 7. Overview Dashboard Manager
const overviewManager = {
  update(parsedDataset) {
    if (!parsedDataset) return;

    // A. Update Top KPI Cards
    const kpiTotalLanes = document.getElementById('kpiTotalLanes');
    const kpiTotalLanesSub = document.getElementById('kpiTotalLanesSub');
    const kpiAssignedLanes = document.getElementById('kpiAssignedLanes');
    const kpiAssignedLanesSub = document.getElementById('kpiAssignedLanesSub');
    const kpiActiveTransporters = document.getElementById('kpiActiveTransporters');
    const kpiActiveTransportersSub = document.getElementById('kpiActiveTransportersSub');
    const kpiTotalCost = document.getElementById('kpiTotalCost');
    const kpiTotalCostSub = document.getElementById('kpiTotalCostSub');

    if (kpiTotalLanes) kpiTotalLanes.textContent = parsedDataset.stats.laneCount;
    if (kpiTotalLanesSub) kpiTotalLanesSub.textContent = `Active freight corridors`;

    if (kpiAssignedLanes) kpiAssignedLanes.textContent = `Ready`;
    if (kpiAssignedLanesSub) kpiAssignedLanesSub.textContent = `Awaiting DP solve`;

    if (kpiActiveTransporters) kpiActiveTransporters.textContent = parsedDataset.stats.transporterCount;
    if (kpiActiveTransportersSub) kpiActiveTransportersSub.textContent = `Quoting carriers`;

    if (kpiTotalCost) kpiTotalCost.textContent = `₹` + Number(parsedDataset.stats.avgQuote).toLocaleString('en-IN', { maximumFractionDigits: 0 });
    if (kpiTotalCostSub) kpiTotalCostSub.textContent = `Avg quote per lane`;

    // B. Switch from Empty State to Active Content
    const emptyState = document.getElementById('overviewEmptyState');
    const activeContent = document.getElementById('overviewActiveContent');

    if (emptyState) emptyState.style.display = 'none';
    if (activeContent) activeContent.style.display = 'block';

    // C. Populate Lanes Summary Table
    const tbody = document.getElementById('overviewLanesTableBody');
    if (tbody) {
      tbody.innerHTML = '';
      parsedDataset.lanes.forEach(lane => {
        // Count how many transporters quoted on this lane
        let carrierCount = 0;
        parsedDataset.transporters.forEach(t => {
          if (t.laneQuotes.some(q => q.laneId === lane.id)) {
            carrierCount++;
          }
        });

        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td><b>#${lane.id}</b></td>
          <td><b>${lane.origin}</b></td>
          <td><b>${lane.destination}</b></td>
          <td><span class="status-pill info">${carrierCount} Carrier Bids</span></td>
          <td><span class="status-pill success">Verified in DB</span></td>
        `;
        tbody.appendChild(tr);
      });
    }

    const subEl = document.getElementById('overviewActiveDatasetSub');
    if (subEl) {
      subEl.textContent = `Loaded "${parsedDataset.fileName}": ${parsedDataset.stats.laneCount} lanes, ${parsedDataset.stats.transporterCount} carriers, ${parsedDataset.stats.quoteCount} total quotes.`;
    }
  }
};

// 8. Bootstrap on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
  sidebarController.init();
  healthPoller.init();
  appRouter.init();
  datasetManager.init();
  console.log('FreightFox Enterprise Platform v1.0 initialized with Dataset Management.');
});
