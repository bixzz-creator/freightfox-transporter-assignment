/**
 * FREIGHTFOX ENTERPRISE PLATFORM — MASTER APPLICATION CONTROLLER
 * Full Suite: Dataset Ingestion, Bitmask DP Optimization, Live GIS Map, Assignments & Reports
 */

'use strict';

// 1. Central Application State Store
window.AppState = {
  currentView: 'overview',
  dataset: null,              // Holds active tender dataset (lanes, transporters, stats)
  optimizationResult: null,   // Holds backend DP optimization result
  isHealthy: false,
  pendingParsedDataset: null
};

// 2. Resilient API Base URL & Fetch Wrapper
function getApiBaseUrl() {
  // When served over HTTP/HTTPS (local server, Render, custom domain), use relative root
  if (window.location.protocol === 'http:' || window.location.protocol === 'https:') {
    return '';
  }
  // Fallback if opened directly from local disk (file:// protocol) during offline browser inspection
  return 'http://localhost:8080';
}

async function apiFetch(path, options = {}) {
  const baseUrl = getApiBaseUrl();
  const url = `${baseUrl}${path}`;
  return fetch(url, options);
}

// 3. View Metadata Registry
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

// 3.5 Enterprise Truck Motion Loader & Section Transition Controller (Lottie Motion)
const truckLoader = {
  overlay: null,
  container: null,
  titleEl: null,
  subtitleDetailEl: null,
  anim: null,
  activeTimer: null,
  isInitialized: false,
  isNavigating: false,

  init() {
    this.overlay = document.getElementById('appGlobalLoader');
    this.container = document.getElementById('truckLoaderAnimation');
    this.titleEl = document.getElementById('loaderTitleText');
    this.subtitleDetailEl = document.getElementById('loaderSubtitleDetail');

    if (!this.container || typeof lottie === 'undefined') return;

    try {
      const animData = window.FREIGHTFOX_TRUCK_LOADER_DATA || null;
      if (animData) {
        this.anim = lottie.loadAnimation({
          container: this.container,
          renderer: 'svg',
          loop: true,
          autoplay: true,
          animationData: animData
        });
      } else {
        this.anim = lottie.loadAnimation({
          container: this.container,
          renderer: 'svg',
          loop: true,
          autoplay: true,
          path: 'assets/freightfox-truck-loader.json'
        });
      }
      this.isInitialized = true;
    } catch (err) {
      console.warn('Lottie truck loader initialization error:', err);
    }
  },

  show(title = 'FreightFox Logistics', subtitle = 'Synchronizing workspace...', minDuration = 420) {
    if (this.titleEl) this.titleEl.textContent = title;
    if (this.subtitleDetailEl) this.subtitleDetailEl.textContent = subtitle;
    if (this.overlay) this.overlay.classList.add('active');
    if (this.anim) {
      this.anim.goToAndPlay(0, true);
    }

    if (this.activeTimer) clearTimeout(this.activeTimer);
    return new Promise(resolve => {
      this.activeTimer = setTimeout(() => {
        resolve();
      }, minDuration);
    });
  },

  hide() {
    if (this.activeTimer) {
      clearTimeout(this.activeTimer);
      this.activeTimer = null;
    }
    if (this.overlay) {
      this.overlay.classList.remove('active');
    }
  },

  async showForNavigation(viewName) {
    if (this.isNavigating) return;
    this.isNavigating = true;

    const titles = {
      overview: 'Overview Dashboard',
      optimization: 'Lane Optimization',
      map: 'Live Transportation Map',
      assignments: 'Lane Allocation Registry',
      dataset: 'Tender Dataset Management',
      reports: 'Procurement Reports & Analytics'
    };
    const subtitles = {
      overview: 'Aggregating procurement metrics & coverage...',
      optimization: 'Initializing Bitmask DP solver environment...',
      map: 'Projecting highway corridors & truck markers...',
      assignments: 'Filtering awarded bids & carrier quotes...',
      dataset: 'Synchronizing matrix rate cards & tender data...',
      reports: 'Compiling financial audits & spend distributions...'
    };

    const title = titles[viewName] || 'FreightFox Platform';
    const subtitle = subtitles[viewName] || 'Loading logistics view...';

    await this.show(title, subtitle, 380);
    this.hide();
    this.isNavigating = false;
  },

  async triggerRefresh() {
    await this.show('Refreshing Logistics Workspace', 'Re-synchronizing backend state & tenders...', 600);
    if (typeof datasetManager !== 'undefined' && datasetManager && datasetManager.syncFromBackend) {
      try {
        await datasetManager.syncFromBackend();
      } catch (err) {
        console.warn('Sync from backend warning:', err);
      }
    }
    if (typeof healthPoller !== 'undefined' && healthPoller) healthPoller.checkHealth();
    if (typeof overviewManager !== 'undefined' && overviewManager && typeof overviewManager.render === 'function') {
      overviewManager.render();
    }
    if (typeof mapEngine !== 'undefined' && mapEngine && mapEngine.map) {
      mapEngine.map.invalidateSize();
      mapEngine.fitAllRoutes();
    }
    this.hide();
  }
};

// 4. Application Router
const appRouter = {
  hasBooted: false,

  init() {
    window.addEventListener('hashchange', () => this.handleHashChange());
    this.handleHashChange(true);
  },

  navigate(viewName) {
    if (!VIEW_METADATA[viewName]) {
      viewName = 'overview';
    }
    if (window.location.hash === `#${viewName}`) {
      this.handleHashChange(false);
    } else {
      window.location.hash = viewName;
    }
  },

  handleHashChange(isInitial = false) {
    let hash = window.location.hash.replace('#', '').trim();
    if (!hash || !VIEW_METADATA[hash]) {
      hash = 'overview';
    }

    if (isInitial && !this.hasBooted) {
      this.hasBooted = true;
      truckLoader.show('FreightFox Logistics', 'Initializing system components & tenders...', 700).then(() => {
        truckLoader.hide();
      });
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
      let pillHtml = `<span class="status-pill info" id="activeDatasetPill">No Dataset Loaded</span>`;
      if (window.AppState.optimizationResult) {
        pillHtml = `<span class="status-pill success" id="activeDatasetPill">Optimal Assignment Solved</span>`;
      } else if (window.AppState.dataset) {
        pillHtml = `<span class="status-pill success" id="activeDatasetPill">Active Dataset: ${window.AppState.dataset.lanes.length} Lanes</span>`;
      }
      headingTitleEl.innerHTML = `${meta.title} ${pillHtml}`;
    }
    if (headingDescEl) headingDescEl.textContent = meta.description;

    // D. Update Page Document Title
    document.title = `FreightFox — ${meta.title}`;

    // E. Close Mobile Sidebar if Open & Scroll to Top
    sidebarController.close();
    const appMain = document.querySelector('.app-main');
    if (appMain) {
      appMain.scrollTop = 0;
    }

    // F. Notify specific view listeners
    window.dispatchEvent(new CustomEvent('viewChanged', { detail: { viewName } }));
  }
};

// 5. Responsive Sidebar Controller (Desktop Collapsed Mini Mode & Mobile Drawer)
const sidebarController = {
  sidebar: null,
  backdrop: null,
  toggleBtn: null,

  init() {
    this.sidebar = document.getElementById('appSidebar');
    this.backdrop = document.getElementById('sidebarBackdrop');
    this.toggleBtn = document.getElementById('sidebarToggleBtn');

    // Restore desktop collapsed preference from localStorage
    if (window.innerWidth > 768) {
      const isSavedCollapsed = localStorage.getItem('freightfox_sidebar_collapsed') === 'true';
      if (isSavedCollapsed && this.sidebar) {
        this.sidebar.classList.add('sidebar-collapsed');
        if (this.toggleBtn) {
          this.toggleBtn.classList.add('is-active');
          this.toggleBtn.setAttribute('aria-expanded', 'false');
        }
      } else if (this.toggleBtn) {
        this.toggleBtn.setAttribute('aria-expanded', 'true');
      }
    }

    if (this.toggleBtn) {
      this.toggleBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this.toggle();
      });
    }

    if (this.backdrop) {
      this.backdrop.addEventListener('click', () => {
        this.closeMobileDrawer();
      });
    }

    document.querySelectorAll('.nav-link').forEach(link => {
      link.addEventListener('click', () => {
        if (window.innerWidth <= 768) {
          this.closeMobileDrawer();
        }
      });
    });

    window.addEventListener('resize', () => {
      if (window.innerWidth > 768) {
        this.closeMobileDrawer();
      }
    });
  },

  toggle() {
    if (!this.sidebar) return;
    if (window.innerWidth <= 768) {
      // Mobile drawer toggle
      const isOpen = this.sidebar.classList.contains('sidebar-open');
      if (isOpen) {
        this.closeMobileDrawer();
      } else {
        this.openMobileDrawer();
      }
    } else {
      // Desktop collapse / expand toggle
      this.sidebar.classList.toggle('sidebar-collapsed');
      const isCollapsed = this.sidebar.classList.contains('sidebar-collapsed');
      localStorage.setItem('freightfox_sidebar_collapsed', isCollapsed ? 'true' : 'false');
      if (this.toggleBtn) {
        this.toggleBtn.classList.toggle('is-active', isCollapsed);
        this.toggleBtn.setAttribute('aria-expanded', isCollapsed ? 'false' : 'true');
      }
      // Notify map engine to re-fit canvas dynamically after layout transition
      if (typeof mapEngine !== 'undefined' && mapEngine && mapEngine.map) {
        setTimeout(() => {
          mapEngine.map.invalidateSize();
        }, 280);
      }
    }
  },

  openMobileDrawer() {
    if (this.sidebar) this.sidebar.classList.add('sidebar-open');
    if (this.backdrop) this.backdrop.classList.add('active');
    document.body.style.overflow = 'hidden';
  },

  closeMobileDrawer() {
    if (this.sidebar) this.sidebar.classList.remove('sidebar-open');
    if (this.backdrop) this.backdrop.classList.remove('active');
    document.body.style.overflow = '';
  },

  close() {
    if (window.innerWidth <= 768) {
      this.closeMobileDrawer();
    }
  }
};

// 6. System Health Poller (Actuator Integration)
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
    setInterval(() => this.checkHealth(), 15000);
  },

  async checkHealth() {
    try {
      const res = await apiFetch('/actuator/health');
      if (res.ok) {
        const data = await res.json();
        if (data.status === 'UP') {
          window.AppState.isHealthy = true;
          if (this.text) this.text.textContent = 'System Online';
          if (this.dot) {
            this.dot.style.backgroundColor = '#10b981';
            this.dot.style.boxShadow = '0 0 8px #10b981';
          }
          if (this.dbVal) {
            const dbName = data.components?.db?.details?.database || '';
            const isProd = (window.location.hostname.includes('render.com')) ||
                           (window.location.protocol === 'https:') ||
                           (dbName.toLowerCase().includes('postgres'));
            this.dbVal.textContent = isProd ? 'PostgreSQL (Neon)' : (dbName || 'Local H2');
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

// 6.5 Interactive Operator & System Diagnostics Controller
const userProfileManager = {
  wrapper: null,
  btn: null,
  dropdown: null,
  isOpen: false,

  init() {
    this.wrapper = document.getElementById('userProfileWrapper');
    this.btn = document.getElementById('userProfileBtn');
    this.dropdown = document.getElementById('userProfileDropdown');

    if (!this.btn || !this.dropdown) return;

    this.btn.addEventListener('click', (e) => {
      e.stopPropagation();
      this.toggle();
    });

    // Close when clicking anywhere outside
    document.addEventListener('click', (e) => {
      if (this.isOpen && this.wrapper && !this.wrapper.contains(e.target)) {
        this.close();
      }
    });

    // Unload active dataset from inside dropdown
    const btnUnload = document.getElementById('profileBtnUnloadDataset');
    if (btnUnload) {
      btnUnload.addEventListener('click', () => {
        this.close();
        if (datasetManager && datasetManager.unloadDataset) {
          datasetManager.unloadDataset();
        }
      });
    }

    // Persona chip switcher
    const chips = this.dropdown.querySelectorAll('.persona-chip');
    chips.forEach(chip => {
      chip.addEventListener('click', (e) => {
        e.stopPropagation();
        chips.forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        const roleName = chip.getAttribute('data-persona');
        this.setPersona(roleName);
      });
    });

    this.refreshSystemDiagnostics();
  },

  toggle() {
    if (this.isOpen) {
      this.close();
    } else {
      this.open();
    }
  },

  open() {
    this.isOpen = true;
    if (this.dropdown) this.dropdown.style.display = 'block';
    if (this.wrapper) this.wrapper.classList.add('open');
    if (this.btn) this.btn.setAttribute('aria-expanded', 'true');
    this.refreshSystemDiagnostics();
  },

  close() {
    this.isOpen = false;
    if (this.dropdown) this.dropdown.style.display = 'none';
    if (this.wrapper) this.wrapper.classList.remove('open');
    if (this.btn) this.btn.setAttribute('aria-expanded', 'false');
  },

  setPersona(roleName) {
    const headerRole = document.getElementById('headerOperatorRole');
    const dropdownRole = document.getElementById('dropdownOperatorRole');
    if (headerRole) headerRole.textContent = roleName;
    if (dropdownRole) dropdownRole.textContent = roleName;
  },

  async refreshSystemDiagnostics() {
    try {
      const startTime = performance.now();
      const statusRes = await apiFetch('/api/v1/transporters/status');
      const latency = Math.max(1, Math.round(performance.now() - startTime));

      if (statusRes.ok) {
        const data = await statusRes.json();
        
        // Update ping latency
        const pingBadge = document.getElementById('dropdownPingBadge');
        if (pingBadge) pingBadge.textContent = `${latency}ms ping`;

        // Update server and db status with live cloud metadata
        const isCloud = window.location.protocol === 'https:' || window.location.hostname.includes('render.com');
        const activePort = data.port || (isCloud ? 10000 : 8080);

        const serverStatus = document.getElementById('dropdownServerStatus');
        if (serverStatus) serverStatus.textContent = `Spring Boot (${activePort})`;

        const sidebarPort = document.getElementById('sidebarTargetPort');
        if (sidebarPort) sidebarPort.textContent = isCloud ? `Cloud Port (${activePort})` : `${activePort} (Spring Boot)`;

        const dbStatus = document.getElementById('dropdownDbStatus');
        if (dbStatus) {
          if (data.databaseStatus === 'CONNECTED') {
            const isProd = (data.environment && data.environment.toLowerCase().includes('prod')) ||
                           isCloud ||
                           (data.database && data.database.toLowerCase().includes('postgres'));
            dbStatus.textContent = isProd ? 'PostgreSQL (Neon)' : (data.database || 'PostgreSQL (Neon)');
          } else {
            dbStatus.textContent = 'Offline';
          }
        }

        const dbModeEl = document.getElementById('dbModeVal');
        if (dbModeEl) {
          const isProd = (data.environment && data.environment.toLowerCase().includes('prod')) ||
                         isCloud ||
                         (data.database && data.database.toLowerCase().includes('postgres'));
          dbModeEl.textContent = isProd ? 'PostgreSQL (Neon)' : 'Local / H2 Mode';
        }

        const envStatus = document.getElementById('dropdownEnvStatus');
        if (envStatus) envStatus.textContent = (data.environment || (isCloud ? 'PROD' : 'LOCAL')).toUpperCase();

        // Update active database metrics
        if (data.activeDataset) {
          const lanesEl = document.getElementById('dropdownLanesCount');
          const transEl = document.getElementById('dropdownTransportersCount');
          const quotesEl = document.getElementById('dropdownQuotesCount');
          if (lanesEl) lanesEl.textContent = data.activeDataset.lanesCount || 0;
          if (transEl) transEl.textContent = data.activeDataset.transportersCount || 0;
          if (quotesEl) quotesEl.textContent = data.activeDataset.quotesCount || 0;
        }

        // Update operator info
        if (data.operator) {
          const opName = document.getElementById('headerOperatorName');
          const dropName = document.getElementById('dropdownOperatorName');
          if (opName && data.operator.displayName) opName.textContent = data.operator.displayName;
          if (dropName && data.operator.displayName) dropName.textContent = data.operator.displayName;
        }

        // Live Actuator Health ping
        const actRes = await apiFetch('/actuator/health');
        if (actRes.ok) {
          const actData = await actRes.json();
          const actEl = document.getElementById('dropdownActuatorHealth');
          if (actEl) {
            actEl.textContent = actData.status || 'UP';
            actEl.className = `profile-health-pill ${actData.status || 'UP'}`;
          }
        }
      }
    } catch (e) {
      console.warn('System diagnostics check error:', e);
    }
  }
};

// 7. Dataset Manager Controller (Upload, Validation, Persistence, Dynamic State & Atomic Replacement)
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

  // Active state controls
  btnReplaceDataset: null,
  btnDownloadTemplateActive: null,
  btnClearActiveDataset: null,

  // Replace Confirmation Modal
  replaceModal: null,
  btnCloseReplaceModal: null,
  btnCancelReplace: null,
  btnConfirmReplace: null,
  pendingReplacement: null,

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

    this.btnReplaceDataset = document.getElementById('btnReplaceDataset');
    this.btnDownloadTemplateActive = document.getElementById('btnDownloadTemplateActive');
    this.btnClearActiveDataset = document.getElementById('btnClearActiveDataset');

    this.replaceModal = document.getElementById('replaceDatasetModal');
    this.btnCloseReplaceModal = document.getElementById('btnCloseReplaceModal');
    this.btnCancelReplace = document.getElementById('btnCancelReplace');
    this.btnConfirmReplace = document.getElementById('btnConfirmReplace');

    // Clicking "Select CSV File" triggers native file picker
    if (this.btnSelectCsv && this.fileInput) {
      this.btnSelectCsv.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.fileInput.value = '';
        this.fileInput.click();
      });
    }

    // Clicking "Replace Dataset" button triggers file picker
    if (this.btnReplaceDataset && this.fileInput) {
      this.btnReplaceDataset.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.fileInput.value = '';
        this.fileInput.click();
      });
    }

    // Download template from active state
    if (this.btnDownloadTemplateActive) {
      this.btnDownloadTemplateActive.addEventListener('click', (e) => {
        e.stopPropagation();
        this.downloadTemplate();
      });
    }

    // Clear / Unload active dataset
    if (this.btnClearActiveDataset) {
      this.btnClearActiveDataset.addEventListener('click', (e) => {
        e.stopPropagation();
        this.unloadDataset();
      });
    }

    // Modal listeners
    if (this.btnCloseReplaceModal) {
      this.btnCloseReplaceModal.addEventListener('click', () => this.cancelReplace());
    }
    if (this.btnCancelReplace) {
      this.btnCancelReplace.addEventListener('click', () => this.cancelReplace());
    }
    if (this.btnConfirmReplace) {
      this.btnConfirmReplace.addEventListener('click', () => this.confirmReplace());
    }
    if (this.replaceModal) {
      this.replaceModal.addEventListener('click', (e) => {
        if (e.target === this.replaceModal) {
          this.cancelReplace();
        }
      });
    }

    // Clicking drop zone triggers file input ONLY if in empty state and user didn't click an inner button
    if (this.dropZone && this.fileInput) {
      this.dropZone.addEventListener('click', (e) => {
        if (this.dropZone.classList.contains('has-dataset')) {
          // Do not trigger file dialog on general card click in active state
          return;
        }
        if (e.target.closest('button') || e.target.closest('a')) {
          return;
        }
        this.fileInput.value = '';
        this.fileInput.click();
      });

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

    // Auto-synchronize persisted dataset and optimization state from database on startup/refresh
    this.syncFromBackend();
  },

  async syncFromBackend() {
    try {
      const res = await apiFetch('/api/v1/transporters/current');
      if (res.ok) {
        const data = await res.json();
        if (data.status === 'LOADED' && data.lanes && data.lanes.length > 0) {
          console.log('Restoring active dataset from backend database:', data);
          const reconstructed = {
            fileName: 'persisted_tender_dataset.csv',
            format: 'Database Persisted',
            lanes: data.lanes,
            transporters: data.transporters,
            stats: {
              laneCount: data.lanesCount,
              transporterCount: data.transportersCount,
              quoteCount: data.quotesCount,
              avgQuote: data.averageQuote,
              minQuote: data.minQuote,
              maxQuote: data.maxQuote
            }
          };
          window.AppState.dataset = reconstructed;
          this.updateDropZoneState(reconstructed);
          overviewManager.updateWithDataset(reconstructed);
          optimizationManager.updateWithDataset(reconstructed);

          // Check if latest optimization is solved in backend
          try {
            const optRes = await apiFetch('/api/v1/transporters/assignment/latest');
            if (optRes.ok) {
              const optData = await optRes.json();
              if (optData.status === 'SUCCESS' && optData.assignments) {
                console.log('Restoring latest optimization result from backend:', optData);
                window.AppState.optimizationResult = optData;
                overviewManager.updateWithOptimization(optData);
                if (optimizationManager && optimizationManager.renderResults) {
                  optimizationManager.renderResults(optData, 0);
                }
                if (mapManager && mapManager.update) {
                  mapManager.update(optData.assignments);
                }
                if (assignmentsManager && assignmentsManager.update) {
                  assignmentsManager.update(optData.assignments);
                }
                if (reportsManager && reportsManager.update) {
                  reportsManager.update(optData);
                }
              }
            }
          } catch (optErr) {
            console.log('No prior assignment solved yet');
          }
        }
      }
    } catch (err) {
      console.warn('Backend dataset sync error:', err);
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

      // Check if a dataset is already active: if so, ask user to confirm replacement!
      if (window.AppState.dataset) {
        this.promptReplaceDataset(parsed);
      } else {
        // Initial upload: apply and persist directly
        this.applyDataset(parsed);
      }
    } catch (err) {
      this.showValidation(`Error parsing CSV: ${err.message}`, 'error');
      if (this.previewCard) this.previewCard.style.display = 'none';
      if (this.successBanner) this.successBanner.style.display = 'none';
    }
  },

  promptReplaceDataset(newParsed) {
    this.pendingReplacement = newParsed;
    const current = window.AppState.dataset;

    if (!this.replaceModal) {
      if (confirm(`An active dataset "${current.fileName}" is already loaded. Do you want to replace it with "${newParsed.fileName}"?`)) {
        this.applyDataset(newParsed);
      }
      return;
    }

    const currNameEl = document.getElementById('replaceModalCurrentName');
    const currStatsEl = document.getElementById('replaceModalCurrentStats');
    const newNameEl = document.getElementById('replaceModalNewName');
    const newStatsEl = document.getElementById('replaceModalNewStats');

    if (currNameEl) currNameEl.textContent = current.fileName;
    if (currStatsEl) currStatsEl.textContent = `${current.stats.laneCount} Lanes • ${current.stats.transporterCount} Transporters • ${current.stats.quoteCount} Quotes`;

    if (newNameEl) newNameEl.textContent = newParsed.fileName;
    if (newStatsEl) newStatsEl.textContent = `${newParsed.stats.laneCount} Lanes • ${newParsed.stats.transporterCount} Transporters • ${newParsed.stats.quoteCount} Quotes (${newParsed.format})`;

    this.replaceModal.style.display = 'flex';
  },

  confirmReplace() {
    if (this.replaceModal) this.replaceModal.style.display = 'none';
    if (this.pendingReplacement) {
      const newDataset = this.pendingReplacement;
      this.pendingReplacement = null;
      this.applyDataset(newDataset);
    }
  },

  cancelReplace() {
    if (this.replaceModal) this.replaceModal.style.display = 'none';
    this.pendingReplacement = null;
    this.showValidation(`Dataset replacement cancelled. Active dataset "${window.AppState.dataset?.fileName || ''}" preserved.`, 'info');
  },

  applyDataset(parsed) {
    window.AppState.pendingParsedDataset = parsed;

    this.showValidation(
      `✓ Parsed "${parsed.fileName}" (${parsed.format}): ${parsed.stats.laneCount} lanes, ${parsed.stats.transporterCount} transporters, ${parsed.stats.quoteCount} quotations.`,
      'success'
    );

    this.renderPreview(parsed);
    this.updateDropZoneState(parsed);

    // Automatically persist atomically to backend
    this.persistToBackend();
  },

  updateDropZoneState(dataset) {
    const dropZone = document.getElementById('dropZone');
    const emptyState = document.getElementById('dropZoneEmptyState');
    const activeState = document.getElementById('dropZoneActiveState');

    if (!dropZone || !emptyState || !activeState) return;

    if (dataset) {
      dropZone.classList.add('has-dataset');
      emptyState.style.display = 'none';
      activeState.style.display = 'block';

      const fileNameEl = document.getElementById('activeDatasetFileName');
      const formatChip = document.getElementById('activeDatasetFormatChip');
      const laneEl = document.getElementById('activeDatasetLaneCount');
      const transporterEl = document.getElementById('activeDatasetTransporterCount');
      const quoteEl = document.getElementById('activeDatasetQuoteCount');
      const avgQuoteEl = document.getElementById('activeDatasetAvgQuote');

      if (fileNameEl) fileNameEl.textContent = dataset.fileName;
      if (formatChip) formatChip.textContent = dataset.format;
      if (laneEl) laneEl.textContent = dataset.stats.laneCount;
      if (transporterEl) transporterEl.textContent = dataset.stats.transporterCount;
      if (quoteEl) quoteEl.textContent = dataset.stats.quoteCount;
      if (avgQuoteEl) {
        avgQuoteEl.textContent = '₹' + Number(dataset.stats.avgQuote).toLocaleString('en-IN', { minimumFractionDigits: 2 });
      }
    } else {
      dropZone.classList.remove('has-dataset');
      emptyState.style.display = 'block';
      activeState.style.display = 'none';
      if (this.previewCard) this.previewCard.style.display = 'none';
      if (this.successBanner) this.successBanner.style.display = 'none';
    }
  },

  async unloadDataset() {
    if (!confirm("Are you sure you want to unload the current dataset? This will atomically clear all persisted lanes and quotations from the database.")) {
      return;
    }
    try {
      truckLoader.show('Unloading Dataset', 'Atomically clearing database records...', 300);
      const res = await apiFetch('/api/v1/transporters', {
        method: 'DELETE'
      });
      truckLoader.hide();

      if (res.ok) {
        window.AppState.dataset = null;
        window.AppState.pendingParsedDataset = null;
        window.AppState.optimizationResult = null;

        this.updateDropZoneState(null);
        this.showValidation("Dataset unloaded and database cleared atomically. Ready for new CSV upload.", "info");

        // Reset Active Pill in Header
        const activePill = document.getElementById('activeDatasetPill');
        if (activePill) {
          activePill.className = 'status-pill default';
          activePill.textContent = 'No Dataset Loaded';
        }

        // Synchronize Overview and Optimization
        overviewManager.updateWithDataset(null);
        optimizationManager.updateWithDataset(null);
        if (userProfileManager) {
          userProfileManager.refreshSystemDiagnostics();
        }
      } else {
        this.showValidation("Failed to clear database.", "error");
      }
    } catch (err) {
      truckLoader.hide();
      this.showValidation("Error clearing database: " + err.message, "error");
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
      truckLoader.show('Persisting Tender Dataset', 'Saving rate matrix and persisting to database...', 500);

      const payload = {
        lanes: parsed.lanes,
        transporters: parsed.transporters
      };

      const res = await apiFetch('/api/v1/transporters/input', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || `HTTP ${res.status}: Failed to persist dataset.`);
      }

      const result = await res.json();

      // Update state
      window.AppState.dataset = parsed;
      window.AppState.optimizationResult = null; // Reset prior optimization

      // Ensure drop zone is in active state!
      this.updateDropZoneState(parsed);

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

      // Synchronize all dependent views
      overviewManager.updateWithDataset(parsed);
      optimizationManager.updateWithDataset(parsed);
      if (userProfileManager) userProfileManager.refreshSystemDiagnostics();

      this.showValidation(
        `✓ Dataset successfully saved and persisted atomically! Ready for optimization.`,
        'success'
      );

    } catch (err) {
      this.showValidation(`Database Persistence Error: ${err.message}`, 'error');
    } finally {
      truckLoader.hide();
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

// 8. Lane Optimization Controller (Bitmask DP Solver)
const optimizationManager = {
  maxTransportersInput: null,
  btnRunOptimizer: null,
  validationBanner: null,

  init() {
    this.maxTransportersInput = document.getElementById('maxTransportersInput');
    this.btnRunOptimizer = document.getElementById('btnRunOptimizer');
    this.validationBanner = document.getElementById('optValidationBanner');

    if (this.btnRunOptimizer) {
      this.btnRunOptimizer.addEventListener('click', () => {
        this.runOptimization();
      });
    }
  },

  updateWithDataset(parsed) {
    const emptyState = document.getElementById('optEmptyState');
    const activeWorkspace = document.getElementById('optActiveWorkspace');
    const resultsBanner = document.getElementById('optResultsBanner');

    if (!parsed) {
      if (emptyState) emptyState.style.display = 'block';
      if (activeWorkspace) activeWorkspace.style.display = 'none';
      if (resultsBanner) resultsBanner.style.display = 'none';
      return;
    }

    if (emptyState) emptyState.style.display = 'none';
    if (activeWorkspace) activeWorkspace.style.display = 'block';
    if (resultsBanner) resultsBanner.style.display = 'none';

    document.getElementById('optParamLaneCount').textContent = parsed.stats.laneCount;
    document.getElementById('optParamTransporterCount').textContent = parsed.stats.transporterCount;

    const maxInput = document.getElementById('maxTransportersInput');
    if (maxInput) {
      maxInput.max = parsed.stats.transporterCount;
      // Default to 3, or transporter count if fewer
      maxInput.value = Math.min(3, parsed.stats.transporterCount);
    }
    const hint = document.getElementById('maxTransportersHint');
    if (hint) {
      hint.textContent = `Total available quoting carriers: ${parsed.stats.transporterCount}. Permitted range: 1 to ${parsed.stats.transporterCount}.`;
    }
  },

  async runOptimization() {
    const dataset = window.AppState.dataset;
    if (!dataset) {
      this.showValidation('Please upload or load a tender dataset first.', 'error');
      return;
    }

    const maxVal = parseInt(this.maxTransportersInput.value, 10);
    if (isNaN(maxVal) || maxVal < 1) {
      this.showValidation('Maximum transporters must be at least 1.', 'error');
      return;
    }

    const btn = this.btnRunOptimizer;
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = `<span>Executing Bitmask DP Optimizer...</span>`;
    }

    const startTime = performance.now();

    try {
      truckLoader.show('Solving Lane Optimization', `Executing Bitmask Dynamic Programming for max ${maxVal} carriers...`, 500);

      const payload = { maxTransporters: maxVal };
      const res = await apiFetch('/api/v1/transporters/assignment', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || `HTTP ${res.status}: Optimization failed.`);
      }

      const result = await res.json();
      const solveTimeMs = Math.round(performance.now() - startTime);

      // Store in AppState
      window.AppState.optimizationResult = result;

      // Update Optimization Results View
      this.renderResults(result, solveTimeMs);

      // Update Map View
      mapManager.update(result.assignments);

      // Update Assignments View
      assignmentsManager.update(result.assignments);

      // Update Reports View
      reportsManager.update(result);

      // Update Overview Dashboard with solved state
      overviewManager.updateWithOptimization(result);

      // Update badge in sidebar
      const mapBadge = document.getElementById('navMapBadge');
      if (mapBadge) {
        mapBadge.style.display = 'inline-block';
        mapBadge.textContent = `${result.assignments.length} Routes`;
      }

      if (userProfileManager) userProfileManager.refreshSystemDiagnostics();

      this.showValidation(`✓ Optimal assignment found: ${result.assignedLanes} lanes allocated to ${result.selectedTransporters.length} distinct transporters.`, 'success');

    } catch (err) {
      this.showValidation(`Optimization Error: ${err.message}`, 'error');
    } finally {
      truckLoader.hide();
      if (btn) {
        btn.disabled = false;
        btn.innerHTML = `
          <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z"/>
          </svg>
          <span>Run Bitmask DP Optimizer</span>
        `;
      }
    }
  },

  renderResults(result, solveTimeMs) {
    const banner = document.getElementById('optResultsBanner');
    if (!banner) return;

    banner.style.display = 'block';

    document.getElementById('optResultTotalCost').textContent =
      '₹' + Number(result.totalCost).toLocaleString('en-IN', { minimumFractionDigits: 2 });
    document.getElementById('optResultTransportersCount').textContent =
      result.selectedTransporters ? result.selectedTransporters.length : result.transporterCount;
    document.getElementById('optResultCoverage').textContent =
      `${result.assignedLanes} / ${result.totalLanes} (100%)`;
    document.getElementById('optResultSolveTime').textContent = `${solveTimeMs}ms`;

    const badgesContainer = document.getElementById('optSelectedTransporterBadges');
    if (badgesContainer) {
      badgesContainer.innerHTML = '';
      (result.selectedTransporters || []).forEach(t => {
        const badge = document.createElement('span');
        badge.className = 'status-pill success';
        badge.style.cssText = 'background: #eff6ff; color: #1d4ed8; border: 1px solid #bfdbfe; font-size: 0.8125rem; font-weight: 700; padding: 4px 10px; border-radius: 6px;';
        badge.textContent = `${t.transporterName} (ID: ${t.transporterId})`;
        badgesContainer.appendChild(badge);
      });
    }

    // Instantly reset workspace scroll to top
    const appMain = document.querySelector('.app-main');
    if (appMain) {
      appMain.scrollTop = 0;
    }
  },

  showValidation(message, type) {
    if (!this.validationBanner) return;
    this.validationBanner.style.display = 'block';
    this.validationBanner.className = `validation-banner ${type}`;
    this.validationBanner.textContent = message;
  }
};

// 9. Interactive GIS Map Controller
const mapManager = {
  init() {
    mapEngine.init();
  },

  update(assignments) {
    mapEngine.renderAssignments(assignments);
    const countBadge = document.getElementById('mapRouteCountBadge');
    if (countBadge) {
      countBadge.textContent = `${assignments.length} Routes Active`;
    }
    const legendBadge = document.getElementById('legendTotalCountBadge');
    if (legendBadge) {
      legendBadge.textContent = assignments.length;
    }
  }
};

// 10. Assignments Registry & Comparison Drawer Controller
const assignmentsManager = {
  currentFilter: 'ALL',
  selectedLaneId: null,

  init() {
    const filterSelect = document.getElementById('carrierFilterSelect');
    if (filterSelect) {
      filterSelect.addEventListener('change', (e) => {
        this.currentFilter = e.target.value;
        this.renderTable();
      });
    }

    const btnModalMap = document.getElementById('btnModalFocusOnMap');
    if (btnModalMap) {
      btnModalMap.addEventListener('click', () => {
        if (this.selectedLaneId) {
          this.closeModal();
          appRouter.navigate('map');
          setTimeout(() => {
            mapEngine.focusLane(this.selectedLaneId);
          }, 300);
        }
      });
    }
  },

  update(assignments) {
    const emptyState = document.getElementById('assignmentsEmptyState');
    const content = document.getElementById('assignmentsContentWrapper');

    if (emptyState) emptyState.style.display = 'none';
    if (content) content.style.display = 'block';

    // Populate filter select options
    const filterSelect = document.getElementById('carrierFilterSelect');
    if (filterSelect) {
      filterSelect.innerHTML = `<option value="ALL">All Selected Transporters</option>`;
      const uniqueCarriers = new Map();
      assignments.forEach(a => {
        uniqueCarriers.set(a.transporterId, a.transporterName);
      });
      uniqueCarriers.forEach((name, id) => {
        const opt = document.createElement('option');
        opt.value = id;
        opt.textContent = `${name} (ID: ${id})`;
        filterSelect.appendChild(opt);
      });
    }

    this.renderTable();
  },

  renderTable() {
    const result = window.AppState.optimizationResult;
    if (!result || !result.assignments) return;

    const tbody = document.getElementById('assignmentTableBody');
    if (!tbody) return;

    tbody.innerHTML = '';

    const filtered = result.assignments.filter(a => {
      if (this.currentFilter === 'ALL') return true;
      return String(a.transporterId) === String(this.currentFilter);
    });

    const countText = document.getElementById('assignmentTableCountText');
    if (countText) {
      countText.textContent = `Showing ${filtered.length} of ${result.assignments.length} assigned lanes`;
    }

    filtered.forEach((a, idx) => {
      const color = geoRoutingService.getLaneColor(idx);
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>
          <div style="display:flex; align-items:center; gap:8px;">
            <span style="display:inline-block; width:10px; height:10px; border-radius:50%; background:${color};"></span>
            <b>#${a.laneId}</b>
          </div>
        </td>
        <td><b>${a.origin}</b></td>
        <td><b>${a.destination}</b></td>
        <td>
          <b>${a.transporterName}</b>
          <small style="color:var(--text-muted); margin-left:4px;">(ID: ${a.transporterId})</small>
        </td>
        <td>
          <span class="quote-badge">₹${Number(a.assignedQuote).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
        </td>
        <td>
          <span class="status-pill success">Optimal</span>
        </td>
        <td>
          <div style="display:flex; gap:6px;">
            <button class="btn btn-outline btn-sm" style="padding:4px 8px; font-size:0.75rem;" onclick="assignmentsManager.focusOnMap(${a.laneId})">
              Map View
            </button>
            <button class="btn btn-outline btn-sm" style="padding:4px 8px; font-size:0.75rem;" onclick="assignmentsManager.openModal(${a.laneId})">
              Compare Bids
            </button>
          </div>
        </td>
      `;
      tbody.appendChild(tr);
    });
  },

  focusOnMap(laneId) {
    appRouter.navigate('map');
    setTimeout(() => {
      mapEngine.focusLane(laneId);
    }, 300);
  },

  openModal(laneId) {
    this.selectedLaneId = laneId;
    const modal = document.getElementById('bidComparisonModal');
    if (!modal) return;

    const result = window.AppState.optimizationResult;
    const dataset = window.AppState.dataset;
    if (!result || !dataset) return;

    const assignment = result.assignments.find(x => x.laneId === laneId);
    if (!assignment) return;

    document.getElementById('modalLaneTitle').textContent = `Lane #${laneId} Quotation Breakdown`;
    document.getElementById('modalLaneCorridor').textContent = `${assignment.origin} → ${assignment.destination}`;
    document.getElementById('modalWinningCarrierText').textContent =
      `${assignment.transporterName} (₹${Number(assignment.assignedQuote).toLocaleString('en-IN', { minimumFractionDigits: 2 })})`;

    // Gather all quotes submitted for this lane
    const allQuotesForLane = [];
    dataset.transporters.forEach(t => {
      const q = t.laneQuotes.find(lq => lq.laneId === laneId);
      if (q) {
        allQuotesForLane.push({
          transporterId: t.id,
          transporterName: t.name,
          quote: q.quote
        });
      }
    });

    allQuotesForLane.sort((a, b) => a.quote - b.quote);

    const highestQuote = Math.max(...allQuotesForLane.map(x => x.quote));
    const savings = highestQuote - assignment.assignedQuote;

    document.getElementById('modalSavingsAmount').textContent =
      `₹${Number(savings).toLocaleString('en-IN', { minimumFractionDigits: 2 })} saved vs highest bid`;

    const tbody = document.getElementById('modalQuotesTableBody');
    if (tbody) {
      tbody.innerHTML = '';
      allQuotesForLane.forEach(q => {
        const isWinning = (q.transporterId === assignment.transporterId);
        const variance = q.quote - assignment.assignedQuote;
        const tr = document.createElement('tr');
        if (isWinning) {
          tr.style.backgroundColor = 'var(--emerald-50)';
        }
        tr.innerHTML = `
          <td>
            <b>${q.transporterName}</b> <small style="color:var(--text-muted);">(ID: ${q.transporterId})</small>
            ${isWinning ? '<span class="status-pill success" style="margin-left:6px;">WINNING BID</span>' : ''}
          </td>
          <td><b>₹${Number(q.quote).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</b></td>
          <td>
            ${isWinning ? '<span style="color:#059669; font-weight:700;">Optimal (Baseline)</span>' : `+₹${Number(variance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`}
          </td>
          <td>
            ${isWinning ? '<span class="status-pill success">Assigned</span>' : '<span class="status-pill neutral">Not Selected</span>'}
          </td>
        `;
        tbody.appendChild(tr);
      });
    }

    modal.classList.add('active');
  },

  closeModal() {
    const modal = document.getElementById('bidComparisonModal');
    if (modal) modal.classList.remove('active');
  }
};

// 11. Reports & Executive Analytics Controller
const reportsManager = {
  update(result) {
    const emptyState = document.getElementById('reportsEmptyState');
    const content = document.getElementById('reportsContentWrapper');

    if (emptyState) emptyState.style.display = 'none';
    if (content) content.style.display = 'block';

    const dataset = window.AppState.dataset;

    // Update print header metadata
    const printTimeEl = document.getElementById('reportPrintTimestamp');
    const printDatasetEl = document.getElementById('reportPrintDatasetName');
    const printLanesEl = document.getElementById('reportPrintLanesCount');
    if (printTimeEl) printTimeEl.textContent = new Date().toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
    if (printDatasetEl && dataset) printDatasetEl.textContent = dataset.fileName;
    if (printLanesEl && result) printLanesEl.textContent = `${result.totalLanes} Corridors (${result.assignedLanes} Allocated)`;

    // Calculate baseline cost (sum of max quotes per lane)
    let baselineCost = 0;
    if (dataset) {
      dataset.lanes.forEach(lane => {
        let maxQuoteForLane = 0;
        dataset.transporters.forEach(t => {
          const q = t.laneQuotes.find(lq => lq.laneId === lane.id);
          if (q && q.quote > maxQuoteForLane) {
            maxQuoteForLane = q.quote;
          }
        });
        baselineCost += maxQuoteForLane;
      });
    }

    const totalSpend = result.totalCost;
    const savings = Math.max(0, baselineCost - totalSpend);
    const savingsPct = baselineCost > 0 ? ((savings / baselineCost) * 100).toFixed(1) : '0.0';

    document.getElementById('reportTotalSpend').textContent =
      '₹' + Number(totalSpend).toLocaleString('en-IN', { minimumFractionDigits: 2 });
    document.getElementById('reportBaselineCost').textContent =
      '₹' + Number(baselineCost).toLocaleString('en-IN', { minimumFractionDigits: 2 });
    document.getElementById('reportCostSavings').textContent =
      '₹' + Number(savings).toLocaleString('en-IN', { minimumFractionDigits: 2 });
    document.getElementById('reportSavingsPercent').textContent =
      `${savingsPct}% procurement cost reduction achieved`;
    document.getElementById('reportCarriersCount').textContent =
      result.selectedTransporters ? result.selectedTransporters.length : result.transporterCount;

    // Carrier spend table
    const carrierSpendMap = new Map();
    result.assignments.forEach(a => {
      const existing = carrierSpendMap.get(a.transporterId) || {
        name: a.transporterName,
        lanesCount: 0,
        totalQuote: 0
      };
      existing.lanesCount++;
      existing.totalQuote += a.assignedQuote;
      carrierSpendMap.set(a.transporterId, existing);
    });

    const carrierTbody = document.getElementById('reportCarrierTableBody');
    if (carrierTbody) {
      carrierTbody.innerHTML = '';
      carrierSpendMap.forEach((val, id) => {
        const sharePct = ((val.totalQuote / totalSpend) * 100).toFixed(1);
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td><b>${val.name}</b> <small style="color:var(--text-muted);">(ID: ${id})</small></td>
          <td><b>${val.lanesCount} lanes</b></td>
          <td><b>₹${Number(val.totalQuote).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</b></td>
          <td><b>${sharePct}%</b></td>
          <td class="hide-in-print">
            <div class="carrier-share-progress">
              <div class="carrier-share-fill" style="width: ${sharePct}%;"></div>
            </div>
          </td>
        `;
        carrierTbody.appendChild(tr);
      });
    }

    // Lane by lane audit table
    const laneTbody = document.getElementById('reportLanesTableBody');
    if (laneTbody) {
      laneTbody.innerHTML = '';
      result.assignments.forEach(a => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td><b>#${a.laneId}</b></td>
          <td><b>${a.origin}</b></td>
          <td><b>${a.destination}</b></td>
          <td><b>${a.transporterName}</b> <small style="color:var(--text-muted);">(ID: ${a.transporterId})</small></td>
          <td><span class="quote-badge">₹${Number(a.assignedQuote).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span></td>
        `;
        laneTbody.appendChild(tr);
      });
    }
  },

  printReport() {
    const printTimeEl = document.getElementById('reportPrintTimestamp');
    if (printTimeEl) {
      printTimeEl.textContent = new Date().toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
    }
    window.print();
  },

  exportCSV() {
    const result = window.AppState.optimizationResult;
    const dataset = window.AppState.dataset;
    if (!result) {
      alert('Please run optimization before exporting the report.');
      return;
    }

    // Calculate baseline cost
    let baselineCost = 0;
    if (dataset) {
      dataset.lanes.forEach(lane => {
        let maxQuoteForLane = 0;
        dataset.transporters.forEach(t => {
          const q = t.laneQuotes.find(lq => lq.laneId === lane.id);
          if (q && q.quote > maxQuoteForLane) {
            maxQuoteForLane = q.quote;
          }
        });
        baselineCost += maxQuoteForLane;
      });
    }

    const totalSpend = result.totalCost;
    const savings = Math.max(0, baselineCost - totalSpend);
    const savingsPct = baselineCost > 0 ? ((savings / baselineCost) * 100).toFixed(1) : '0.0';
    const carrierCount = result.selectedTransporters ? result.selectedTransporters.length : result.transporterCount;

    let csv = '';
    csv += 'FREIGHTFOX ENTERPRISE LOGISTICS - PROCUREMENT AUDIT REPORT\r\n';
    csv += `Generated Date,${new Date().toLocaleString('en-IN')}\r\n`;
    csv += `Tender Dataset,${dataset ? dataset.fileName : 'Active Dataset'}\r\n`;
    csv += `Optimization Engine,Bitmask Dynamic Programming (Global Optimality)\r\n`;
    csv += `Total Procurement Spend (INR),${totalSpend.toFixed(2)}\r\n`;
    csv += `Baseline Ceiling Cost (INR),${baselineCost.toFixed(2)}\r\n`;
    csv += `Net Cost Savings (INR),${savings.toFixed(2)} (${savingsPct}%)\r\n`;
    csv += `Total Lanes,${result.totalLanes}\r\n`;
    csv += `Assigned Lanes,${result.assignedLanes} (100% Coverage)\r\n`;
    csv += `Winning Transporters,${carrierCount}\r\n\r\n`;

    // Section 1: Carrier Allocation Breakdown
    csv += 'CARRIER EXPENDITURE ALLOCATION\r\n';
    csv += 'Transporter ID,Transporter Name,Assigned Lanes Count,Total Spend (INR),Spend Share (%)\r\n';

    const carrierSpendMap = new Map();
    result.assignments.forEach(a => {
      const existing = carrierSpendMap.get(a.transporterId) || {
        name: a.transporterName,
        lanesCount: 0,
        totalQuote: 0
      };
      existing.lanesCount++;
      existing.totalQuote += a.assignedQuote;
      carrierSpendMap.set(a.transporterId, existing);
    });

    carrierSpendMap.forEach((val, id) => {
      const sharePct = ((val.totalQuote / totalSpend) * 100).toFixed(1);
      csv += `${id},"${val.name}",${val.lanesCount},${val.totalQuote.toFixed(2)},${sharePct}%\r\n`;
    });

    csv += '\r\n';

    // Section 2: Lane-by-Lane Allocations
    csv += 'LANE-BY-LANE AWARDED ALLOCATIONS\r\n';
    csv += 'Lane ID,Origin Corridor,Destination Hub,Awarded Transporter ID,Awarded Transporter Name,Contracted Rate (INR),Status\r\n';
    result.assignments.forEach(a => {
      csv += `${a.laneId},"${a.origin}","${a.destination}",${a.transporterId},"${a.transporterName}",${a.assignedQuote.toFixed(2)},Optimal\r\n`;
    });

    // Trigger download
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `freightfox_procurement_report_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }
};

// 12. Overview Dashboard Manager
const overviewManager = {
  currentLanes: [],
  currentAssignments: null,

  render() {
    if (window.AppState && window.AppState.optimizationResult) {
      this.updateWithOptimization(window.AppState.optimizationResult);
    } else if (window.AppState && window.AppState.dataset) {
      this.updateWithDataset(window.AppState.dataset);
    }
  },

  viewLaneOnMap(laneId) {
    appRouter.navigate('map');
    setTimeout(() => {
      mapEngine.focusLane(laneId);
    }, 300);
  },

  filterLanes(query) {
    const q = (query || '').trim().toLowerCase();
    if (this.currentAssignments && this.currentAssignments.length > 0) {
      const filtered = this.currentAssignments.filter(a => {
        return !q ||
          a.origin.toLowerCase().includes(q) ||
          a.destination.toLowerCase().includes(q) ||
          String(a.laneId).includes(q) ||
          (a.transporterName && a.transporterName.toLowerCase().includes(q));
      });
      this.renderAssignmentRows(filtered);
    } else if (this.currentLanes && this.currentLanes.length > 0) {
      const filtered = this.currentLanes.filter(l => {
        return !q ||
          l.origin.toLowerCase().includes(q) ||
          l.destination.toLowerCase().includes(q) ||
          String(l.id).includes(q);
      });
      this.renderPreSolveRows(filtered);
    }
  },

  renderPreSolveRows(lanes) {
    const tbody = document.getElementById('overviewLanesTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    lanes.forEach(lane => {
      let carrierCount = 0;
      if (window.AppState && window.AppState.dataset && window.AppState.dataset.transporters) {
        window.AppState.dataset.transporters.forEach(t => {
          if (t.laneQuotes && t.laneQuotes.some(q => q.laneId === lane.id)) {
            carrierCount++;
          }
        });
      }

      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><b>#${lane.id}</b></td>
        <td><b>${lane.origin}</b></td>
        <td><b>${lane.destination}</b></td>
        <td><span class="status-pill info">${carrierCount} Carrier Bids</span></td>
        <td>—</td>
        <td><span class="status-pill info">Awaiting Solve</span></td>
        <td style="text-align: right;">
          <button class="btn btn-outline btn-xs" onclick="overviewManager.viewLaneOnMap(${lane.id})" title="Inspect on Live Map">
            <svg width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" style="margin-right:2px; vertical-align:middle;">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7"/>
            </svg>
            View on Map →
          </button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  },

  renderAssignmentRows(assignments) {
    const tbody = document.getElementById('overviewLanesTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    assignments.forEach((a, idx) => {
      const color = geoRoutingService.getLaneColor(idx);
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>
          <div style="display:flex; align-items:center; gap:8px;">
            <span style="display:inline-block; width:10px; height:10px; border-radius:50%; background:${color};"></span>
            <b>#${a.laneId}</b>
          </div>
        </td>
        <td><b>${a.origin}</b></td>
        <td><b>${a.destination}</b></td>
        <td><b>${a.transporterName}</b> <small style="color:var(--text-muted);">(ID: ${a.transporterId})</small></td>
        <td><span class="quote-badge">₹${Number(a.assignedQuote).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span></td>
        <td><span class="status-pill success">Optimal</span></td>
        <td style="text-align: right;">
          <button class="btn btn-outline btn-xs" onclick="overviewManager.viewLaneOnMap(${a.laneId})" title="Inspect on Live Map">
            <svg width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" style="margin-right:2px; vertical-align:middle;">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7"/>
            </svg>
            View on Map →
          </button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  },

  updateWithDataset(parsedDataset) {
    const emptyState = document.getElementById('overviewEmptyState');
    const activeContent = document.getElementById('overviewActiveContent');
    const kpiTotalLanes = document.getElementById('kpiTotalLanes');
    const kpiTotalLanesSub = document.getElementById('kpiTotalLanesSub');
    const kpiAssignedLanes = document.getElementById('kpiAssignedLanes');
    const kpiAssignedLanesSub = document.getElementById('kpiAssignedLanesSub');
    const kpiActiveTransporters = document.getElementById('kpiActiveTransporters');
    const kpiActiveTransportersSub = document.getElementById('kpiActiveTransportersSub');
    const kpiTotalCost = document.getElementById('kpiTotalCost');
    const kpiTotalCostSub = document.getElementById('kpiTotalCostSub');

    if (!parsedDataset) {
      this.currentLanes = [];
      this.currentAssignments = null;
      if (emptyState) emptyState.style.display = 'block';
      if (activeContent) activeContent.style.display = 'none';
      if (kpiTotalLanes) kpiTotalLanes.textContent = '—';
      if (kpiTotalLanesSub) kpiTotalLanesSub.textContent = 'Awaiting dataset ingestion';
      if (kpiAssignedLanes) kpiAssignedLanes.textContent = '—';
      if (kpiAssignedLanesSub) kpiAssignedLanesSub.textContent = '0% coverage';
      if (kpiActiveTransporters) kpiActiveTransporters.textContent = '—';
      if (kpiActiveTransportersSub) kpiActiveTransportersSub.textContent = 'Max limit constrained';
      if (kpiTotalCost) kpiTotalCost.textContent = '—';
      if (kpiTotalCostSub) kpiTotalCostSub.textContent = 'Optimal procurement cost';
      return;
    }

    this.currentLanes = parsedDataset.lanes || [];
    this.currentAssignments = null;

    if (kpiTotalLanes) kpiTotalLanes.textContent = parsedDataset.stats.laneCount;
    if (kpiTotalLanesSub) kpiTotalLanesSub.textContent = `Active freight corridors`;

    if (kpiAssignedLanes) kpiAssignedLanes.textContent = `Ready`;
    if (kpiAssignedLanesSub) kpiAssignedLanesSub.textContent = `Awaiting DP solve`;

    if (kpiActiveTransporters) kpiActiveTransporters.textContent = parsedDataset.stats.transporterCount;
    if (kpiActiveTransportersSub) kpiActiveTransportersSub.textContent = `Quoting carriers`;

    if (kpiTotalCost) kpiTotalCost.textContent = `₹` + Number(parsedDataset.stats.avgQuote).toLocaleString('en-IN', { maximumFractionDigits: 0 });
    if (kpiTotalCostSub) kpiTotalCostSub.textContent = `Avg quote per lane`;

    // Switch from Empty State to Active Content
    if (emptyState) emptyState.style.display = 'none';
    if (activeContent) activeContent.style.display = 'block';

    // Populate Lanes Summary Table
    this.renderPreSolveRows(this.currentLanes);

    const subEl = document.getElementById('overviewActiveDatasetSub');
    if (subEl) {
      subEl.textContent = `Loaded "${parsedDataset.fileName}": ${parsedDataset.stats.laneCount} lanes, ${parsedDataset.stats.transporterCount} carriers, ${parsedDataset.stats.quoteCount} total quotes.`;
    }
  },

  updateWithOptimization(result) {
    if (!result) return;
    this.currentAssignments = result.assignments || [];

    // Update Top KPI Cards with solved metrics
    const kpiAssignedLanes = document.getElementById('kpiAssignedLanes');
    const kpiAssignedLanesSub = document.getElementById('kpiAssignedLanesSub');
    const kpiActiveTransporters = document.getElementById('kpiActiveTransporters');
    const kpiActiveTransportersSub = document.getElementById('kpiActiveTransportersSub');
    const kpiTotalCost = document.getElementById('kpiTotalCost');
    const kpiTotalCostSub = document.getElementById('kpiTotalCostSub');

    if (kpiAssignedLanes) kpiAssignedLanes.textContent = `${result.assignedLanes} / ${result.totalLanes}`;
    if (kpiAssignedLanesSub) kpiAssignedLanesSub.textContent = `100% Lane Coverage`;

    const carrierCount = result.selectedTransporters ? result.selectedTransporters.length : result.transporterCount;
    if (kpiActiveTransporters) kpiActiveTransporters.textContent = carrierCount;
    if (kpiActiveTransportersSub) kpiActiveTransportersSub.textContent = `Distinct carriers used`;

    if (kpiTotalCost) kpiTotalCost.textContent = `₹` + Number(result.totalCost).toLocaleString('en-IN', { maximumFractionDigits: 0 });
    if (kpiTotalCostSub) kpiTotalCostSub.textContent = `Total procurement spend`;

    // Update Status Banner
    const bannerTitle = document.getElementById('overviewStatusBannerTitle');
    const bannerDesc = document.getElementById('overviewStatusBannerDesc');
    const bannerActions = document.getElementById('overviewStatusBannerActions');

    if (bannerTitle) bannerTitle.textContent = '✓ Optimal Assignment Solved';
    if (bannerDesc) bannerDesc.textContent = `Lexicographic objective satisfied: ${carrierCount} carriers utilized, total procurement cost ₹${Number(result.totalCost).toLocaleString('en-IN', { minimumFractionDigits: 2 })}.`;
    if (bannerActions) {
      bannerActions.innerHTML = `
        <button class="btn btn-primary btn-sm" onclick="appRouter.navigate('map')">
          View on Live Map →
        </button>
      `;
    }

    // Populate Transporter Allocation Card
    const allocCard = document.getElementById('overviewCarrierAllocationCard');
    const allocBars = document.getElementById('overviewCarrierBars');
    if (allocCard && allocBars) {
      allocCard.style.display = 'block';
      allocBars.innerHTML = '';

      const carrierStats = new Map();
      result.assignments.forEach(a => {
        const item = carrierStats.get(a.transporterId) || {
          name: a.transporterName,
          count: 0,
          spend: 0
        };
        item.count++;
        item.spend += a.assignedQuote;
        carrierStats.set(a.transporterId, item);
      });

      carrierStats.forEach((v, id) => {
        const sharePct = ((v.spend / result.totalCost) * 100).toFixed(1);
        const div = document.createElement('div');
        div.className = 'carrier-share-bar-container';
        div.innerHTML = `
          <div class="carrier-share-bar-header">
            <span><b>${v.name}</b> <small style="color:var(--text-muted);">(ID: ${id}) — ${v.count} lanes</small></span>
            <span><b>₹${Number(v.spend).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</b> (${sharePct}%)</span>
          </div>
          <div class="carrier-share-progress">
            <div class="carrier-share-fill" style="width: ${sharePct}%;"></div>
          </div>
        `;
        allocBars.appendChild(div);
      });
    }

    // Render assignment rows with "View on Map" action button
    this.renderAssignmentRows(this.currentAssignments);
  }
};

// 13. Bootstrap on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
  truckLoader.init();
  sidebarController.init();
  healthPoller.init();
  userProfileManager.init();
  appRouter.init();
  datasetManager.init();
  optimizationManager.init();
  mapManager.init();
  assignmentsManager.init();
  console.log('FreightFox Enterprise Platform v1.0 initialized.');
});
