/**
 * FREIGHTFOX ENTERPRISE PLATFORM — GEO-ROUTING SERVICE
 * Accurate Geocoding Registry & OSRM Real Road Routing Integration
 */

'use strict';

const CITY_COORDINATES = {
  "mumbai":        { lat: 19.0760, lng: 72.8777, label: "Mumbai" },
  "delhi":         { lat: 28.6139, lng: 77.2090, label: "Delhi" },
  "bangalore":     { lat: 12.9716, lng: 77.5946, label: "Bangalore" },
  "bengaluru":     { lat: 12.9716, lng: 77.5946, label: "Bengaluru" },
  "chennai":       { lat: 13.0827, lng: 80.2707, label: "Chennai" },
  "kolkata":       { lat: 22.5726, lng: 88.3639, label: "Kolkata" },
  "pune":          { lat: 18.5204, lng: 73.8567, label: "Pune" },
  "hyderabad":     { lat: 17.3850, lng: 78.4867, label: "Hyderabad" },
  "ahmedabad":     { lat: 23.0225, lng: 72.5714, label: "Ahmedabad" },
  "jaipur":        { lat: 26.9124, lng: 75.7873, label: "Jaipur" },
  "guwahati":      { lat: 26.1445, lng: 91.7362, label: "Guwahati" },
  "shillong":      { lat: 25.5788, lng: 91.8933, label: "Shillong" },
  "lucknow":       { lat: 26.8467, lng: 80.9462, label: "Lucknow" },
  "patna":         { lat: 25.5941, lng: 85.1376, label: "Patna" },
  "ranchi":        { lat: 23.3441, lng: 85.3096, label: "Ranchi" },
  "bhubaneswar":   { lat: 20.2961, lng: 85.8245, label: "Bhubaneswar" },
  "surat":         { lat: 21.1702, lng: 72.8311, label: "Surat" },
  "indore":        { lat: 22.7196, lng: 75.8577, label: "Indore" },
  "nagpur":        { lat: 21.1458, lng: 79.0882, label: "Nagpur" },
  "raipur":        { lat: 21.2514, lng: 81.6296, label: "Raipur" },
  "chandigarh":    { lat: 30.7333, lng: 76.7794, label: "Chandigarh" },
  "shimla":        { lat: 31.1048, lng: 77.1734, label: "Shimla" },
  "coimbatore":    { lat: 11.0168, lng: 76.9558, label: "Coimbatore" },
  "kochi":         { lat: 9.9312,  lng: 76.2673, label: "Kochi" },
  "visakhapatnam": { lat: 17.6868, lng: 83.2185, label: "Visakhapatnam" },
  "vijayawada":    { lat: 16.5062, lng: 80.6480, label: "Vijayawada" },
  "bhopal":        { lat: 23.2599, lng: 77.4126, label: "Bhopal" },
  "jabalpur":      { lat: 23.1815, lng: 79.9864, label: "Jabalpur" }
};

// Distinct high-contrast color palette for routes
const LANE_COLOR_PALETTE = [
  '#2563eb', // Blue
  '#10b981', // Emerald
  '#f59e0b', // Amber
  '#8b5cf6', // Violet
  '#ec4899', // Pink
  '#06b6d4', // Cyan
  '#ea580c', // Deep Orange
  '#14b8a6', // Teal
  '#6366f1', // Indigo
  '#84cc16'  // Lime
];

const geoRoutingService = {
  routeCache: new Map(),

  getLaneColor(laneIndex) {
    return LANE_COLOR_PALETTE[laneIndex % LANE_COLOR_PALETTE.length];
  },

  geocodeCity(cityName) {
    if (!cityName) return null;
    const clean = cityName.toLowerCase().trim().replace(/^(origin|destination)\s*/i, '');

    // Check direct dictionary
    if (CITY_COORDINATES[clean]) {
      return CITY_COORDINATES[clean];
    }

    // Fuzzy check
    for (const [key, coord] of Object.entries(CITY_COORDINATES)) {
      if (clean.includes(key) || key.includes(clean)) {
        return coord;
      }
    }

    // Default fallback to central India with slight deterministic offset
    const hash = clean.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0);
    return {
      lat: 20.5937 + ((hash % 10) - 5) * 0.8,
      lng: 78.9629 + (((hash * 3) % 10) - 5) * 0.8,
      label: cityName,
      isApproximate: true
    };
  },

  async fetchRoute(originCoord, destCoord) {
    const cacheKey = `${originCoord.lat},${originCoord.lng}-${destCoord.lat},${destCoord.lng}`;
    if (this.routeCache.has(cacheKey)) {
      return this.routeCache.get(cacheKey);
    }

    try {
      // Call public OSRM Driving API (real highway road geometry)
      const url = `https://router.project-osrm.org/route/v1/driving/${originCoord.lng},${originCoord.lat};${destCoord.lng},${destCoord.lat}?overview=full&geometries=geojson`;
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 4000);

      const res = await fetch(url, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (res.ok) {
        const data = await res.json();
        if (data.routes && data.routes.length > 0) {
          const route = data.routes[0];
          // Convert GeoJSON [lng, lat] to Leaflet [lat, lng]
          const latLngs = route.geometry.coordinates.map(c => [c[1], c[0]]);
          const distanceKm = Math.round(route.distance / 1000);
          const durationHours = (route.duration / 3600).toFixed(1);

          const result = {
            coordinates: latLngs,
            distanceKm,
            durationHours,
            isRealRoad: true
          };
          this.routeCache.set(cacheKey, result);
          return result;
        }
      }
    } catch (err) {
      // OSRM failed or timed out — fallback to smooth curved corridor
    }

    // Fallback: Generate curved corridor line
    const fallbackCoords = this.generateCorridorCurve(originCoord, destCoord);
    const approxKm = Math.round(this.haversineDistance(originCoord, destCoord));
    const result = {
      coordinates: fallbackCoords,
      distanceKm: approxKm,
      durationHours: (approxKm / 60).toFixed(1),
      isRealRoad: false
    };
    this.routeCache.set(cacheKey, result);
    return result;
  },

  generateCorridorCurve(c1, c2) {
    const points = [];
    const steps = 30;
    // Calculate perpendicular offset for curved route
    const midLat = (c1.lat + c2.lat) / 2;
    const midLng = (c1.lng + c2.lng) / 2;
    const dLat = c2.lat - c1.lat;
    const dLng = c2.lng - c1.lng;
    const offset = 0.15; // Arc curvature

    const ctrlLat = midLat - dLng * offset;
    const ctrlLng = midLng + dLat * offset;

    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      // Quadratic Bezier interpolation
      const lat = (1 - t) * (1 - t) * c1.lat + 2 * (1 - t) * t * ctrlLat + t * t * c2.lat;
      const lng = (1 - t) * (1 - t) * c1.lng + 2 * (1 - t) * t * ctrlLng + t * t * c2.lng;
      points.push([lat, lng]);
    }
    return points;
  },

  haversineDistance(c1, c2) {
    const R = 6371; // km
    const dLat = (c2.lat - c1.lat) * Math.PI / 180;
    const dLng = (c2.lng - c1.lng) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(c1.lat * Math.PI / 180) * Math.cos(c2.lat * Math.PI / 180) *
              Math.sin(dLng / 2) * Math.sin(dLng / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c * 1.25; // 25% road winding factor
  }
};

