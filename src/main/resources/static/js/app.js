/**
 * FREIGHTFOX ENTERPRISE PLATFORM — APPLICATION CONTROLLER
 * Milestone 2: Navigation Shell & Enterprise UI Controller
 */

'use strict';

// 1. Central Application State Store
window.AppState = {
  currentView: 'overview',
  dataset: null,              // Holds real uploaded tender dataset
  optimizationResult: null,   // Holds real optimization result from backend
  isHealthy: false
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
      // Retain dataset status pill inside heading
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

    // F. Notify specific view listeners if needed
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

// 6. Bootstrap on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
  sidebarController.init();
  healthPoller.init();
  appRouter.init();
  console.log('FreightFox Enterprise Shell v1.0 initialized.');
});
