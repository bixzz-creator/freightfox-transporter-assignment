/**
 * FREIGHTFOX ENTERPRISE PLATFORM — MULTI-LANE MAP ENGINE
 * Leaflet.js Interactive Multi-Route GIS Renderer with Truck Markers & Legend
 * 100% Free Public Basemaps (Esri World Street & OpenStreetMap) — NO API KEY REQUIRED
 */

'use strict';

const mapEngine = {
  map: null,
  routeGroup: null,
  markerGroup: null,
  truckGroup: null,
  activeRoutes: [],
  isInitialized: false,
  baseLayers: null,

  init() {
    if (typeof L === 'undefined') {
      console.warn('Leaflet not loaded yet. Retrying in 200ms...');
      setTimeout(() => this.init(), 200);
      return;
    }

    const container = document.getElementById('leafletMapContainer');
    if (!container || this.isInitialized) return;

    // Centered on India (National Logistics View)
    this.map = L.map('leafletMapContainer', {
      center: [21.5937, 78.9629],
      zoom: 5,
      zoomControl: true,
      scrollWheelZoom: true
    });

    // 1. Esri World Street Map — High-detail highways, cities, terrain (No API Key Required)
    const esriStreet = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}', {
      attribution: 'Tiles &copy; Esri &mdash; National Geographic, DeLorme, NAVTEQ',
      maxZoom: 19
    });

    // 2. OpenStreetMap Standard — Community open data (No API Key Required)
    const osm = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19
    });

    // 3. Esri Clean Light Gray Canvas — Minimalist enterprise SaaS style (No API Key Required)
    const esriCanvas = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}', {
      attribution: 'Tiles &copy; Esri, HERE, Garmin',
      maxZoom: 19
    });

    // Add default layer: Esri World Street (Crisp, real road navigation visual)
    esriStreet.addTo(this.map);

    // Layer switcher control for users
    this.baseLayers = {
      "Highway Street Map": esriStreet,
      "OpenStreetMap": osm,
      "Clean Light Canvas": esriCanvas
    };
    L.control.layers(this.baseLayers, null, { position: 'topleft' }).addTo(this.map);

    this.routeGroup = L.featureGroup().addTo(this.map);
    this.markerGroup = L.featureGroup().addTo(this.map);
    this.truckGroup = L.featureGroup().addTo(this.map);

    this.isInitialized = true;

    // Recalculate map size when map tab becomes visible
    window.addEventListener('viewChanged', (e) => {
      if (e.detail && e.detail.viewName === 'map' && this.map) {
        setTimeout(() => {
          this.map.invalidateSize();
          this.fitAllRoutes();
        }, 150);
      }
    });
  },

  async renderAssignments(assignments) {
    if (!this.isInitialized) {
      this.init();
    }

    this.clearMap();
    if (!assignments || assignments.length === 0) {
      const emptyState = document.getElementById('mapEmptyState');
      const wrapper = document.getElementById('mapContentWrapper');
      if (emptyState) emptyState.style.display = 'block';
      if (wrapper) wrapper.style.display = 'none';
      return;
    }

    const emptyState = document.getElementById('mapEmptyState');
    const wrapper = document.getElementById('mapContentWrapper');
    if (emptyState) emptyState.style.display = 'none';
    if (wrapper) wrapper.style.display = 'block';

    if (this.map) {
      setTimeout(() => this.map.invalidateSize(), 100);
    }

    const legendList = document.getElementById('mapLegendList');
    if (legendList) legendList.innerHTML = '';

    this.activeRoutes = [];

    for (let i = 0; i < assignments.length; i++) {
      const a = assignments[i];
      const color = geoRoutingService.getLaneColor(i);
      const originCoord = geoRoutingService.geocodeCity(a.origin);
      const destCoord = geoRoutingService.geocodeCity(a.destination);

      if (!originCoord || !destCoord) continue;

      // Fetch real road route geometry (OSRM highway routing with fallback)
      const routeData = await geoRoutingService.fetchRoute(originCoord, destCoord);
      const coords = routeData.coordinates;

      if (this.map && this.routeGroup) {
        // Casing outline for high visibility against any basemap
        L.polyline(coords, {
          color: '#ffffff',
          weight: 7,
          opacity: 0.9,
          lineCap: 'round',
          lineJoin: 'round'
        }).addTo(this.routeGroup);

        // Core Colored Polyline
        const polyline = L.polyline(coords, {
          color: color,
          weight: 4.5,
          opacity: 0.95,
          lineCap: 'round',
          lineJoin: 'round'
        }).addTo(this.routeGroup);

        // Polyline popup
        const routePopupHtml = `
          <div style="font-family:'Plus Jakarta Sans',sans-serif; min-width: 220px; padding: 2px;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
              <strong style="color:#0f172a; font-size:1rem;">Lane #${a.laneId}</strong>
              <span style="background:${color}22; color:${color}; font-weight:700; padding:2px 8px; border-radius:4px; font-size:0.75rem;">
                Assigned
              </span>
            </div>
            <div style="font-size:0.875rem; color:#1e293b; font-weight:600; margin-bottom:8px;">
              ${a.origin} <span style="color:#0284c7;">→</span> ${a.destination}
            </div>
            <div style="border-top:1px solid #e2e8f0; padding-top:6px; font-size:0.8125rem; line-height:1.6;">
              <div>Carrier: <b>${a.transporterName}</b> <span style="color:#64748b;">(ID: ${a.transporterId})</span></div>
              <div>Freight Rate: <b style="color:#047857; font-family:'JetBrains Mono';">₹${Number(a.assignedQuote).toLocaleString('en-IN', {minimumFractionDigits:2})}</b></div>
              <div>Route Distance: <b>${routeData.distanceKm} km</b> <span style="color:#64748b; font-size:0.75rem;">(${routeData.isRealRoad ? 'Highway' : 'Corridor'})</span></div>
            </div>
          </div>
        `;
        polyline.bindPopup(routePopupHtml);

        // Origin Hub Marker (Solid colored circle with white inner dot)
        const originIcon = L.divIcon({
          className: 'custom-map-pin origin-pin',
          html: `<div style="background-color: ${color}; width: 16px; height: 16px; border-radius: 50%; border: 3px solid #ffffff; box-shadow: 0 1px 6px rgba(0,0,0,0.4);" title="${a.origin} (Origin)"></div>`,
          iconSize: [16, 16],
          iconAnchor: [8, 8]
        });
        const originMarker = L.marker([originCoord.lat, originCoord.lng], { icon: originIcon }).addTo(this.markerGroup);
        originMarker.bindPopup(`<b>Origin Hub:</b> ${a.origin}<br><span style="font-size:0.8rem; color:#64748b;">Lane #${a.laneId}</span>`);

        // Destination Hub Marker (Dark Navy with colored ring)
        const destIcon = L.divIcon({
          className: 'custom-map-pin dest-pin',
          html: `<div style="background-color: #0b1528; width: 16px; height: 16px; border-radius: 50%; border: 3px solid ${color}; box-shadow: 0 1px 6px rgba(0,0,0,0.4);" title="${a.destination} (Destination)"></div>`,
          iconSize: [16, 16],
          iconAnchor: [8, 8]
        });
        const destMarker = L.marker([destCoord.lat, destCoord.lng], { icon: destIcon }).addTo(this.markerGroup);
        destMarker.bindPopup(`<b>Destination Hub:</b> ${a.destination}<br><span style="font-size:0.8rem; color:#64748b;">Lane #${a.laneId}</span>`);

        // Truck Marker at Corridor Midpoint
        const midIndex = Math.floor(coords.length * 0.5);
        const truckPos = coords[midIndex] || [(originCoord.lat + destCoord.lat)/2, (originCoord.lng + destCoord.lng)/2];

        const truckIcon = L.divIcon({
          className: 'custom-truck-marker',
          html: `
            <div style="background: #ffffff; border: 2.5px solid ${color}; width: 34px; height: 34px; border-radius: 8px; display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 10px rgba(0,0,0,0.25); cursor: pointer;" title="Carrier: ${a.transporterName} | Lane #${a.laneId}">
              <img src="images/delivery.png" alt="Delivery Truck" style="width: 22px; height: 22px; object-fit: contain;">
            </div>
          `,
          iconSize: [34, 34],
          iconAnchor: [17, 17]
        });

        const truckMarker = L.marker(truckPos, { icon: truckIcon }).addTo(this.truckGroup);
        truckMarker.bindPopup(routePopupHtml);

        this.activeRoutes.push({
          laneId: a.laneId,
          color,
          polyline,
          originMarker,
          destMarker,
          truckMarker,
          assignment: a
        });
      }

      // Append to Map Legend
      if (legendList) {
        const item = document.createElement('div');
        item.className = 'legend-route-item';
        item.innerHTML = `
          <div class="legend-route-left">
            <span class="route-color-pill" style="background: ${color};"></span>
            <div>
              <div class="route-meta-lane">Lane #${a.laneId}</div>
              <div class="route-meta-cities">${a.origin} → ${a.destination}</div>
            </div>
          </div>
          <span class="route-carrier-tag">${a.transporterName}</span>
        `;
        item.addEventListener('click', () => {
          this.focusLane(a.laneId);
        });
        legendList.appendChild(item);
      }
    }

    this.fitAllRoutes();
  },

  focusLane(laneId) {
    const r = this.activeRoutes.find(x => x.laneId === laneId);
    if (r && this.map && r.polyline) {
      this.map.fitBounds(r.polyline.getBounds(), { padding: [60, 60], maxZoom: 8 });
      r.polyline.openPopup();
    }
  },

  fitAllRoutes() {
    if (!this.map || !this.routeGroup || this.activeRoutes.length === 0) return;
    const bounds = this.routeGroup.getBounds();
    if (bounds.isValid()) {
      this.map.fitBounds(bounds, { padding: [40, 40] });
    }
  },

  toggleLegend() {
    const legend = document.getElementById('mapFloatingLegend');
    if (!legend) return;
    if (legend.style.display === 'none') {
      legend.style.display = 'flex';
    } else {
      legend.style.display = 'none';
    }
  },

  clearMap() {
    if (this.routeGroup) this.routeGroup.clearLayers();
    if (this.markerGroup) this.markerGroup.clearLayers();
    if (this.truckGroup) this.truckGroup.clearLayers();
    this.activeRoutes = [];
  }
};
