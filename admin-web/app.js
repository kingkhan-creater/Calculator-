// Firebase Web SDK v10 Modular Imports
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.8.0/firebase-app.js";
import { 
  getFirestore, 
  collection, 
  getDocs,
  getDoc, 
  deleteDoc, 
  doc, 
  setDoc, 
  addDoc, 
  serverTimestamp, 
  query, 
  orderBy, 
  limit 
} from "https://www.gstatic.com/firebasejs/10.8.0/firebase-firestore.js";
import { 
  getStorage, 
  ref, 
  deleteObject 
} from "https://www.gstatic.com/firebasejs/10.8.0/firebase-storage.js";
import { 
  getAuth, 
  signInWithEmailAndPassword, 
  GoogleAuthProvider, 
  signInWithPopup, 
  signOut, 
  onAuthStateChanged 
} from "https://www.gstatic.com/firebasejs/10.8.0/firebase-auth.js";

// Exact Firebase Web Configuration supplied by User
const firebaseConfig = {
  apiKey: "AIzaSyCbLH3RoebFWxQRccwo7e3Z0jDE712SMdA",
  authDomain: "sleathcam1.firebaseapp.com",
  projectId: "sleathcam1",
  storageBucket: "sleathcam1.firebasestorage.app",
  messagingSenderId: "50868501006",
  appId: "1:50868501006:web:671f5d82b38fcb99831a5f",
  measurementId: "G-C3SB605T72"
};

// Initialize Firebase Services
const app = initializeApp(firebaseConfig);
const db = getFirestore(app);
const storage = getStorage(app);
const auth = getAuth(app);

// Constants
const FREE_TIER_BYTES_LIMIT = 5 * 1024 * 1024 * 1024; // 5 GB
const SUPER_ADMIN_EMAILS = ["king.khan648k@gmail.com"];
const DEFAULT_PIN = "1234";

// Global State
let currentAdminUser = null;
let recordings = [];
let usersList = [];
let auditLogs = [];
let licenseKeys = [];
let selectedRecordingsIds = new Set();
let activePreviewItem = null;

// DOM Element References
const authOverlay = document.getElementById("authOverlay");
const dashboardApp = document.getElementById("dashboardApp");
const authTabEmail = document.getElementById("authTabEmail");
const authTabPin = document.getElementById("authTabPin");
const emailLoginForm = document.getElementById("emailLoginForm");
const pinLoginForm = document.getElementById("pinLoginForm");
const adminEmailInput = document.getElementById("adminEmailInput");
const adminPasswordInput = document.getElementById("adminPasswordInput");
const adminPinInput = document.getElementById("adminPinInput");
const pinSubmitBtn = document.getElementById("pinSubmitBtn");
const googleLoginBtn = document.getElementById("googleLoginBtn");
const authError = document.getElementById("authError");
const logoutBtn = document.getElementById("logoutBtn");
const refreshDataBtn = document.getElementById("refreshDataBtn");
const pageTitle = document.getElementById("pageTitle");

// Tab Navigation Elements
const navItems = document.querySelectorAll(".nav-item");
const tabPanes = document.querySelectorAll(".tab-pane");

// Dashboard Elements
const dashTotalRecordings = document.getElementById("dashTotalRecordings");
const dashStorageUsed = document.getElementById("dashStorageUsed");
const dashStorageDetail = document.getElementById("dashStorageDetail");
const dashActiveDevices = document.getElementById("dashActiveDevices");
const dashTotalUsers = document.getElementById("dashTotalUsers");
const dashQuotaProgress = document.getElementById("dashQuotaProgress");
const dashQuotaPercent = document.getElementById("dashQuotaPercent");
const dashQuotaRemaining = document.getElementById("dashQuotaRemaining");
const dashRecentTableBody = document.getElementById("dashRecentTableBody");
const quickPurgeBtn = document.getElementById("quickPurgeBtn");
const dashPremiumUsers = document.getElementById("dashPremiumUsers");

// Recordings Elements
const recSearchInput = document.getElementById("recSearchInput");
const recOwnerFilter = document.getElementById("recOwnerFilter");
const recSelectAllCheckbox = document.getElementById("recSelectAllCheckbox");
const recDeleteSelectedBtn = document.getElementById("recDeleteSelectedBtn");
const recSelectedCount = document.getElementById("recSelectedCount");
const recPurgeAllBtn = document.getElementById("recPurgeAllBtn");
const recordingsTableBody = document.getElementById("recordingsTableBody");
const sidebarRecordingsBadge = document.getElementById("sidebarRecordingsBadge");

// Users Elements
const userSearchInput = document.getElementById("userSearchInput");
const refreshUsersBtn = document.getElementById("refreshUsersBtn");
const usersTableBody = document.getElementById("usersTableBody");

// License Keys Elements
const licTotalKeys = document.getElementById("licTotalKeys");
const licActiveKeys = document.getElementById("licActiveKeys");
const licAvailableKeys = document.getElementById("licAvailableKeys");
const sidebarLicensesBadge = document.getElementById("sidebarLicensesBadge");
const generateKeyForm = document.getElementById("generateKeyForm");
const keyPlanSelect = document.getElementById("keyPlanSelect");
const keyCustomerInput = document.getElementById("keyCustomerInput");
const newKeyAlert = document.getElementById("newKeyAlert");
const newKeyCodeText = document.getElementById("newKeyCodeText");
const newKeyPlanText = document.getElementById("newKeyPlanText");
const btnCopyKey = document.getElementById("btnCopyKey");
const btnCopyWhatsAppMsg = document.getElementById("btnCopyWhatsAppMsg");
const refreshKeysBtn = document.getElementById("refreshKeysBtn");
const licenseKeysTableBody = document.getElementById("licenseKeysTableBody");

// Dynamic Limits & Quota Elements
const dynamicLimitsForm = document.getElementById("dynamicLimitsForm");
const limitFreeRecordings = document.getElementById("limitFreeRecordings");
const limitFreeStorageMb = document.getElementById("limitFreeStorageMb");
const limitPremiumStorageGb = document.getElementById("limitPremiumStorageGb");

// Cloudinary Settings Elements
const cloudinaryConfigForm = document.getElementById("cloudinaryConfigForm");
const cloudNameInput = document.getElementById("cloudNameInput");
const uploadPresetInput = document.getElementById("uploadPresetInput");
const apiKeyInput = document.getElementById("apiKeyInput");
const apiSecretInput = document.getElementById("apiSecretInput");

// Storage Elements
const storageUsedDisplay = document.getElementById("storageUsedDisplay");
const storageFreeDisplay = document.getElementById("storageFreeDisplay");
const cleanOldestBtn = document.getElementById("cleanOldestBtn");
const cleanLargestBtn = document.getElementById("cleanLargestBtn");
const cleanAllGuestBtn = document.getElementById("cleanAllGuestBtn");

// Audit Elements
const refreshAuditBtn = document.getElementById("refreshAuditBtn");
const auditTableBody = document.getElementById("auditTableBody");

// Video Modal Elements
const videoModal = document.getElementById("videoModal");
const videoPlayer = document.getElementById("videoPlayer");
const modalVideoTitle = document.getElementById("modalVideoTitle");
const modalVideoSize = document.getElementById("modalVideoSize");
const modalVideoDuration = document.getElementById("modalVideoDuration");
const modalVideoDevice = document.getElementById("modalVideoDevice");
const modalCloseBtn = document.getElementById("modalCloseBtn");
const modalDownloadBtn = document.getElementById("modalDownloadBtn");
const modalDeleteBtn = document.getElementById("modalDeleteBtn");

// Purge Modal Elements
const confirmPurgeModal = document.getElementById("confirmPurgeModal");
const confirmPurgeCloseBtn = document.getElementById("confirmPurgeCloseBtn");
const cancelPurgeBtn = document.getElementById("cancelPurgeBtn");
const executePurgeBtn = document.getElementById("executePurgeBtn");
const toast = document.getElementById("toast");

// --- 1. Authentication & Session Handling ---
authTabEmail.addEventListener("click", () => {
  authTabEmail.classList.add("active");
  authTabPin.classList.remove("active");
  emailLoginForm.classList.remove("hidden");
  pinLoginForm.classList.add("hidden");
  authError.classList.add("hidden");
});

authTabPin.addEventListener("click", () => {
  authTabPin.classList.add("active");
  authTabEmail.classList.remove("active");
  pinLoginForm.classList.remove("hidden");
  emailLoginForm.classList.add("hidden");
  authError.classList.add("hidden");
});

emailLoginForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  authError.classList.add("hidden");
  const email = adminEmailInput.value.trim();
  const password = adminPasswordInput.value.trim();

  try {
    const userCredential = await signInWithEmailAndPassword(auth, email, password);
    currentAdminUser = userCredential.user;
    unlockAdminDashboard(currentAdminUser.email);
  } catch (err) {
    authError.textContent = "Login Failed: " + err.message;
    authError.classList.remove("hidden");
  }
});

googleLoginBtn.addEventListener("click", async () => {
  authError.classList.add("hidden");
  const provider = new GoogleAuthProvider();
  try {
    const result = await signInWithPopup(auth, provider);
    currentAdminUser = result.user;
    unlockAdminDashboard(currentAdminUser.email);
  } catch (err) {
    authError.textContent = "Google Auth Failed: " + err.message;
    authError.classList.remove("hidden");
  }
});

pinSubmitBtn.addEventListener("click", () => {
  const pin = adminPinInput.value.trim();
  if (pin === DEFAULT_PIN) {
    currentAdminUser = { email: "king.khan648k@gmail.com", uid: "pin_admin" };
    unlockAdminDashboard("king.khan648k@gmail.com (PIN Authorized)");
  } else {
    authError.textContent = "Invalid PIN. Default is 1234.";
    authError.classList.remove("hidden");
  }
});

function unlockAdminDashboard(adminIdentity) {
  authOverlay.classList.add("hidden");
  dashboardApp.classList.remove("hidden");
  sessionStorage.setItem("admin_unlocked", "true");
  sessionStorage.setItem("admin_identity", adminIdentity);
  document.getElementById("sidebarAdminName").textContent = adminIdentity;
  loadAllData();
  recordAuditLog("ADMIN_LOGIN", "Web Dashboard", { identity: adminIdentity });
}

logoutBtn.addEventListener("click", async () => {
  try { await signOut(auth); } catch (_) {}
  sessionStorage.clear();
  currentAdminUser = null;
  dashboardApp.classList.add("hidden");
  authOverlay.classList.remove("hidden");
});

// Check existing session
onAuthStateChanged(auth, (user) => {
  if (user) {
    currentAdminUser = user;
    unlockAdminDashboard(user.email || "Administrator");
  } else if (sessionStorage.getItem("admin_unlocked") === "true") {
    unlockAdminDashboard(sessionStorage.getItem("admin_identity") || "Administrator");
  }
});

// --- 2. Tab Navigation ---
navItems.forEach((item) => {
  item.addEventListener("click", () => {
    const targetTab = item.getAttribute("data-tab");
    navItems.forEach((n) => n.classList.remove("active"));
    tabPanes.forEach((p) => p.classList.remove("active"));

    item.classList.add("active");
    const pane = document.getElementById(targetTab);
    if (pane) pane.classList.add("active");

    const tabNames = {
      "tab-dashboard": "Dashboard Overview",
      "tab-recordings": "Guest Cloud Recordings",
      "tab-users": "Registered User Management",
      "tab-licenses": "No-Gmail License Keys & Activation",
      "tab-storage": "Cloud Storage & Free Tier Quotas",
      "tab-audit": "Administrative Audit & Security Logs",
      "tab-settings": "System & Super Admin Settings",
    };
    pageTitle.textContent = tabNames[targetTab] || "Admin Console";

    if (targetTab === "tab-licenses") {
      loadLicenseKeys();
    } else if (targetTab === "tab-storage") {
      loadDynamicLimits();
    } else if (targetTab === "tab-settings") {
      loadCloudinaryConfig();
    }
  });
});

// --- 3. Data Sync & Telemetry ---
async function loadAllData() {
  await Promise.all([
    loadRecordings(),
    loadUsers(),
    loadAuditLogs(),
    loadLicenseKeys(),
    loadDynamicLimits(),
    loadCloudinaryConfig()
  ]);
}

refreshDataBtn.addEventListener("click", () => {
  loadAllData();
  showToast("Syncing data from sleathcam1 cloud...");
});

// Formatters
function formatBytes(bytes) {
  if (!bytes || bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
}

function formatDuration(ms) {
  if (!ms) return "--";
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}:${sec < 10 ? '0' : ''}${sec}`;
}

function formatDate(epochMs) {
  if (!epochMs) return "Unknown";
  const d = new Date(epochMs);
  return d.toLocaleDateString() + " " + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

// Load Recordings (both from cloud_recordings AND scanning all users' vault_media subcollections)
async function loadRecordings() {
  try {
    const items = [];
    const seenIds = new Set();

    // 1. Fetch from cloud_recordings
    try {
      const snap = await getDocs(query(collection(db, "cloud_recordings")));
      snap.forEach((d) => {
        const data = d.data();
        items.push({ id: d.id, ...data });
        if (data.mediaId) seenIds.add(data.mediaId);
        if (data.cloudinaryPublicId) seenIds.add(data.cloudinaryPublicId);
        seenIds.add(d.id);
      });
    } catch (e) {
      console.warn("Could not query cloud_recordings:", e);
    }

    // 2. Also fetch from users/{uid}/vault_media and users/{uid}/recordings for registered users
    try {
      const usersSnap = await getDocs(collection(db, "users"));
      for (const uDoc of usersSnap.docs) {
        const uData = uDoc.data();
        const uid = uDoc.id;
        const userEmail = uData.email || uData.displayName || uid.substring(0, 8);

        // Fetch user's vault_media subcollection
        try {
          const mediaSnap = await getDocs(collection(db, "users", uid, "vault_media"));
          mediaSnap.forEach((mDoc) => {
            const m = mDoc.data();
            const uniqueKey = m.mediaId || m.cloudinaryPublicId || mDoc.id;
            if (!seenIds.has(uniqueKey)) {
              seenIds.add(uniqueKey);
              items.push({
                id: `user_${uid}_${mDoc.id}`,
                mediaId: m.mediaId || mDoc.id,
                userId: uid,
                userEmail: userEmail,
                ownerType: uData.isPremium ? "PREMIUM" : "REGISTERED",
                fileName: m.fileName || `Media_${mDoc.id}`,
                mediaType: m.mediaType || "VIDEO",
                downloadUrl: m.cloudinarySecureUrl || m.downloadUrl || "",
                cloudinarySecureUrl: m.cloudinarySecureUrl || "",
                cloudinaryPublicId: m.cloudinaryPublicId || "",
                fileSize: m.sizeBytes || 0,
                sizeBytes: m.sizeBytes || 0,
                duration: Math.round((m.durationMs || 0) / 1000),
                durationMs: m.durationMs || 0,
                createdAt: m.backupTimestampMs || m.updatedAtEpochMs || Date.now()
              });
            }
          });
        } catch (_) {}

        // Fetch user's recordings subcollection
        try {
          const recSnap = await getDocs(collection(db, "users", uid, "recordings"));
          recSnap.forEach((rDoc) => {
            const r = rDoc.data();
            const uniqueKey = r.id || rDoc.id;
            if (!seenIds.has(uniqueKey)) {
              seenIds.add(uniqueKey);
              items.push({
                id: `user_${uid}_rec_${rDoc.id}`,
                userId: uid,
                userEmail: userEmail,
                ownerType: uData.isPremium ? "PREMIUM" : "REGISTERED",
                fileName: r.fileName || `Recording_${rDoc.id}`,
                mediaType: "VIDEO",
                downloadUrl: r.downloadUrl || r.cloudinarySecureUrl || "",
                cloudinarySecureUrl: r.cloudinarySecureUrl || "",
                fileSize: r.sizeBytes || r.fileSize || 0,
                sizeBytes: r.sizeBytes || r.fileSize || 0,
                duration: r.duration || Math.round((r.durationMs || 0) / 1000),
                createdAt: r.timestamp || r.createdAt || Date.now()
              });
            }
          });
        } catch (_) {}
      }
    } catch (e) {
      console.warn("Could not query users subcollections:", e);
    }

    // Sort newest first
    items.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
    recordings = items;
    selectedRecordingsIds.clear();

    renderDashboard(recordings);
    renderRecordingsTable(recordings);
    renderStorageMetrics(recordings);
    sidebarRecordingsBadge.textContent = recordings.length;
  } catch (err) {
    console.error("Error fetching recordings:", err);
    showToast("Error reading recordings: " + err.message);
  }
}

// Render Dashboard
function renderDashboard(list) {
  const totalCount = list.length;
  let totalBytes = 0;
  const uniqueDevices = new Set();

  list.forEach((item) => {
    totalBytes += Number(item.sizeBytes || item.fileSize || 0);
    if (item.deviceId) uniqueDevices.add(item.deviceId);
  });

  dashTotalRecordings.textContent = totalCount;
  dashStorageUsed.textContent = formatBytes(totalBytes);
  dashStorageDetail.textContent = `${(totalBytes / (1024 * 1024)).toFixed(1)} MB used of 5.0 GB`;
  dashActiveDevices.textContent = uniqueDevices.size;

  // Quota bar
  const percent = Math.min(100, (totalBytes / FREE_TIER_BYTES_LIMIT) * 100).toFixed(1);
  dashQuotaPercent.textContent = `${percent}% Used`;
  dashQuotaProgress.style.width = `${percent}%`;
  const remainingBytes = Math.max(0, FREE_TIER_BYTES_LIMIT - totalBytes);
  dashQuotaRemaining.textContent = `${formatBytes(remainingBytes)} Free Space Remaining`;

  if (percent > 85) dashQuotaProgress.style.background = "#ef4444";
  else if (percent > 60) dashQuotaProgress.style.background = "#f59e0b";
  else dashQuotaProgress.style.background = "#10b981";

  // Recent Table
  const recents = list.slice(0, 5);
  if (recents.length === 0) {
    dashRecentTableBody.innerHTML = `<tr><td colspan="6" class="loading-state">No active cloud recordings. Free tier 100% clean.</td></tr>`;
  } else {
    dashRecentTableBody.innerHTML = recents.map((item) => `
      <tr>
        <td><strong>${item.fileName || item.id}</strong></td>
        <td><span class="badge ${item.ownerType === 'AUTHENTICATED' ? 'badge-primary' : 'badge-guest'}">${item.ownerType || 'GUEST'} (${(item.anonymousAccountReference || item.deviceId || 'anon').substring(0, 10)})</span></td>
        <td>${formatBytes(item.fileSize || item.sizeBytes)}</td>
        <td>${formatDuration(item.duration || item.durationMs)}</td>
        <td>${formatDate(item.createdAt)}</td>
        <td style="text-align: right;">
          <button class="btn btn-secondary btn-sm" onclick="window.previewRecording('${item.id}')">Play</button>
        </td>
      </tr>
    `).join("");
  }
}

// Render Recordings Table
function renderRecordingsTable(list) {
  if (list.length === 0) {
    recordingsTableBody.innerHTML = `<tr><td colspan="7" class="loading-state">No recordings found.</td></tr>`;
    return;
  }

  recordingsTableBody.innerHTML = list.map((item) => {
    const isChecked = selectedRecordingsIds.has(item.id) ? "checked" : "";
    const name = item.fileName || `Recording_${item.id.substring(0, 8)}.mp4`;
    const ownerType = item.ownerType || "GUEST";
    const refId = item.anonymousAccountReference || item.userId || item.deviceId || "guest";
    const size = formatBytes(item.fileSize || item.sizeBytes);
    const dur = formatDuration(item.duration || item.durationMs);
    const date = formatDate(item.createdAt);

    return `
      <tr>
        <td><input type="checkbox" class="rec-check" data-id="${item.id}" ${isChecked} /></td>
        <td>
          <strong>${name}</strong>
          <div style="font-size: 0.72rem; color: #64748b;">Path: ${item.cloudStoragePath || item.storagePath || 'gs://sleathcam1...'}</div>
        </td>
        <td>
          <span class="badge ${ownerType === 'AUTHENTICATED' ? 'badge-primary' : 'badge-guest'}">${ownerType}</span>
          <span style="font-family: monospace; font-size: 0.75rem; margin-left: 6px;">${refId.substring(0, 14)}...</span>
        </td>
        <td>${size}</td>
        <td>${dur}</td>
        <td>${date}</td>
        <td style="text-align: right;">
          <button class="btn btn-secondary btn-sm" onclick="window.previewRecording('${item.id}')">Play</button>
          <a class="btn btn-secondary btn-sm" href="${item.downloadUrl || '#'}" target="_blank" download="${name}">Download</a>
          <button class="btn btn-danger btn-sm" onclick="window.deleteRecordingPrompt('${item.id}')">Delete</button>
        </td>
      </tr>
    `;
  }).join("");

  document.querySelectorAll(".rec-check").forEach((cb) => {
    cb.addEventListener("change", (e) => {
      const id = e.target.getAttribute("data-id");
      if (e.target.checked) selectedRecordingsIds.add(id);
      else selectedRecordingsIds.delete(id);
      updateSelectionUI();
    });
  });
}

function updateSelectionUI() {
  const count = selectedRecordingsIds.size;
  recSelectedCount.textContent = count;
  recDeleteSelectedBtn.disabled = count === 0;
  recSelectAllCheckbox.checked = count > 0 && count === recordings.length;
}

recSelectAllCheckbox.addEventListener("change", (e) => {
  if (e.target.checked) recordings.forEach((r) => selectedRecordingsIds.add(r.id));
  else selectedRecordingsIds.clear();
  renderRecordingsTable(filterRecordings());
  updateSelectionUI();
});

// Search & Filter
function filterRecordings() {
  const q = (recSearchInput.value || "").toLowerCase().trim();
  const owner = recOwnerFilter.value;

  return recordings.filter((r) => {
    const matchesQuery = !q || 
      (r.fileName || "").toLowerCase().includes(q) ||
      (r.id || "").toLowerCase().includes(q) ||
      (r.deviceId || "").toLowerCase().includes(q) ||
      (r.anonymousAccountReference || "").toLowerCase().includes(q);

    const matchesOwner = owner === "ALL" || (r.ownerType || "GUEST") === owner;
    return matchesQuery && matchesOwner;
  });
}

recSearchInput.addEventListener("input", () => renderRecordingsTable(filterRecordings()));
recOwnerFilter.addEventListener("change", () => renderRecordingsTable(filterRecordings()));

// --- 4. User Management ---
async function loadUsers() {
  try {
    const snap = await getDocs(collection(db, "users"));
    const items = [];
    snap.forEach((d) => items.push({ uid: d.id, ...d.data() }));
    usersList = items;
    dashTotalUsers.textContent = usersList.length;

    // Count premium users and update gold stat card
    const premiumCount = usersList.filter(u => u.isPremium === true).length;
    if (dashPremiumUsers) dashPremiumUsers.textContent = premiumCount;

    renderUsersTable(usersList);
  } catch (err) {
    console.error("Error loading users:", err);
    usersTableBody.innerHTML = `<tr><td colspan="6" class="loading-state">Failed to load users: ${err.message}</td></tr>`;
  }
}

function renderUsersTable(list) {
  if (list.length === 0) {
    usersTableBody.innerHTML = `<tr><td colspan="6" class="loading-state">No registered accounts in system yet.</td></tr>`;
    return;
  }

  usersTableBody.innerHTML = list.map((u) => `
    <tr style="cursor: pointer;" title="Click row or Inspect Data to view user's files and details">
      <td onclick="window.inspectUser('${u.uid}')">
        <div style="font-weight: 600; color: #60a5fa; display: flex; align-items: center; gap: 6px;">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
          ${u.email || u.displayName || 'Registered User'}
        </div>
      </td>
      <td onclick="window.inspectUser('${u.uid}')"><code class="code-pill">${u.uid.substring(0, 16)}...</code></td>
      <td>
        <span class="badge ${u.isPremium ? 'badge-success' : 'badge-guest'}">${u.isPremium ? 'PREMIUM (VIP)' : 'FREE TIER'}</span>
      </td>
      <td>
        <span class="badge ${u.accountStatus === 'SUSPENDED' ? 'badge-danger' : 'badge-success'}">${u.accountStatus || 'ACTIVE'}</span>
      </td>
      <td>${formatDate(u.createdAt || u.createdAtEpochMs || u.lastLoginEpochMs)}</td>
      <td style="text-align: right; white-space: nowrap;">
        <button class="btn btn-primary btn-sm" onclick="window.inspectUser('${u.uid}')" style="margin-right: 4px;">
          🔍 Inspect Data
        </button>
        <button class="btn btn-secondary btn-sm" onclick="window.toggleUserPremium('${u.uid}', ${!u.isPremium})">
          ${u.isPremium ? 'Revoke VIP' : 'Grant VIP'}
        </button>
        <button class="btn ${u.accountStatus === 'SUSPENDED' ? 'btn-secondary' : 'btn-danger'} btn-sm" onclick="window.toggleUserBan('${u.uid}', '${u.accountStatus === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED'}')">
          ${u.accountStatus === 'SUSPENDED' ? 'Unban' : 'Suspend'}
        </button>
      </td>
    </tr>
  `).join("");
}

// User Inspector Logic
let currentInspectedUser = null;
let currentInspectedUserFiles = [];

const userInspectorModal = document.getElementById("userInspectorModal");
const inspectUserEmail = document.getElementById("inspectUserEmail");
const inspectUserUid = document.getElementById("inspectUserUid");
const inspectUserCloseBtn = document.getElementById("inspectUserCloseBtn");
const inspectUserDoneBtn = document.getElementById("inspectUserDoneBtn");
const inspectUserTierBadge = document.getElementById("inspectUserTierBadge");
const inspectBtnTogglePremium = document.getElementById("inspectBtnTogglePremium");
const inspectUserStatusBadge = document.getElementById("inspectUserStatusBadge");
const inspectBtnToggleBan = document.getElementById("inspectBtnToggleBan");
const inspectUserMediaCount = document.getElementById("inspectUserMediaCount");
const inspectUserStorageUsed = document.getElementById("inspectUserStorageUsed");
const inspectUserCreatedDate = document.getElementById("inspectUserCreatedDate");
const inspectUserItemsCount = document.getElementById("inspectUserItemsCount");
const inspectUserMediaTableBody = document.getElementById("inspectUserMediaTableBody");

if (inspectUserCloseBtn) inspectUserCloseBtn.addEventListener("click", () => userInspectorModal.classList.add("hidden"));
if (inspectUserDoneBtn) inspectUserDoneBtn.addEventListener("click", () => userInspectorModal.classList.add("hidden"));

window.inspectUser = async function(userId) {
  try {
    const user = usersList.find((u) => u.uid === userId) || { uid: userId };
    currentInspectedUser = user;

    inspectUserEmail.textContent = user.email || user.displayName || "User Account";
    inspectUserUid.textContent = user.uid;
    inspectUserTierBadge.className = `badge ${user.isPremium ? 'badge-success' : 'badge-guest'}`;
    inspectUserTierBadge.textContent = user.isPremium ? "PREMIUM (VIP)" : "FREE TIER";
    inspectBtnTogglePremium.textContent = user.isPremium ? "Revoke VIP" : "Grant VIP";
    inspectBtnTogglePremium.onclick = async () => {
      await window.toggleUserPremium(user.uid, !user.isPremium);
      const updatedUser = usersList.find((u) => u.uid === user.uid) || user;
      updatedUser.isPremium = !user.isPremium;
      window.inspectUser(user.uid);
    };

    inspectUserStatusBadge.className = `badge ${user.accountStatus === 'SUSPENDED' ? 'badge-danger' : 'badge-success'}`;
    inspectUserStatusBadge.textContent = user.accountStatus || "ACTIVE";
    inspectBtnToggleBan.className = `btn ${user.accountStatus === 'SUSPENDED' ? 'btn-secondary' : 'btn-danger'} btn-sm`;
    inspectBtnToggleBan.textContent = user.accountStatus === 'SUSPENDED' ? "Unban Account" : "Suspend Account";
    inspectBtnToggleBan.onclick = async () => {
      const nextStatus = user.accountStatus === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED';
      await window.toggleUserBan(user.uid, nextStatus);
      user.accountStatus = nextStatus;
      window.inspectUser(user.uid);
    };

    inspectUserCreatedDate.textContent = formatDate(user.createdAt || user.createdAtEpochMs || user.lastLoginEpochMs);
    inspectUserMediaTableBody.innerHTML = `<tr><td colspan="5" class="loading-state">Fetching user recordings and files...</td></tr>`;
    userInspectorModal.classList.remove("hidden");

    // Fetch all files associated with this user
    const userFiles = [];
    const seenMediaKeys = new Set();
    let totalBytes = 0;

    // 1. Check users/{userId}/vault_media
    try {
      const vmSnap = await getDocs(collection(db, "users", userId, "vault_media"));
      vmSnap.forEach((docSnap) => {
        const d = docSnap.data();
        const fid = d.mediaId || docSnap.id;
        if (!seenMediaKeys.has(fid)) {
          seenMediaKeys.add(fid);
          const size = Number(d.sizeBytes || d.fileSize || 0);
          totalBytes += size;
          userFiles.push({
            id: docSnap.id,
            mediaId: fid,
            source: "vault_media",
            fileName: d.fileName || `Media_${docSnap.id}`,
            mediaType: d.mediaType || "VIDEO",
            fileSize: size,
            sizeBytes: size,
            downloadUrl: d.cloudinarySecureUrl || d.downloadUrl || "",
            duration: d.durationMs ? Math.round(d.durationMs / 1000) : (d.duration || 0),
            createdAt: d.backupTimestampMs || d.updatedAtEpochMs || Date.now()
          });
        }
      });
    } catch (e) {
      console.warn("Could not read vault_media for user:", e);
    }

    // 2. Check users/{userId}/recordings
    try {
      const recSnap = await getDocs(collection(db, "users", userId, "recordings"));
      recSnap.forEach((docSnap) => {
        const d = docSnap.data();
        const fid = d.id || docSnap.id;
        if (!seenMediaKeys.has(fid)) {
          seenMediaKeys.add(fid);
          const size = Number(d.sizeBytes || d.fileSize || 0);
          totalBytes += size;
          userFiles.push({
            id: docSnap.id,
            mediaId: fid,
            source: "recordings",
            fileName: d.fileName || `Recording_${docSnap.id}`,
            mediaType: "VIDEO",
            fileSize: size,
            sizeBytes: size,
            downloadUrl: d.downloadUrl || d.cloudinarySecureUrl || "",
            duration: d.duration || (d.durationMs ? Math.round(d.durationMs / 1000) : 0),
            createdAt: d.timestamp || d.createdAt || Date.now()
          });
        }
      });
    } catch (e) {
      console.warn("Could not read recordings for user:", e);
    }

    // 3. Check cloud_recordings
    try {
      const crSnap = await getDocs(query(collection(db, "cloud_recordings")));
      crSnap.forEach((docSnap) => {
        const d = docSnap.data();
        if (d.userId === userId || d.anonymousAccountReference === userId) {
          const fid = d.mediaId || docSnap.id;
          if (!seenMediaKeys.has(fid)) {
            seenMediaKeys.add(fid);
            const size = Number(d.sizeBytes || d.fileSize || 0);
            totalBytes += size;
            userFiles.push({
              id: docSnap.id,
              mediaId: fid,
              source: "cloud_recordings",
              fileName: d.fileName || `Recording_${docSnap.id}`,
              mediaType: d.mediaType || "VIDEO",
              fileSize: size,
              sizeBytes: size,
              downloadUrl: d.downloadUrl || d.cloudinarySecureUrl || "",
              duration: d.duration || 0,
              createdAt: d.createdAt || Date.now()
            });
          }
        }
      });
    } catch (e) {
      console.warn("Could not query cloud_recordings for user:", e);
    }

    // Sort newest first
    userFiles.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
    currentInspectedUserFiles = userFiles;

    inspectUserMediaCount.textContent = `${userFiles.length} Files`;
    inspectUserStorageUsed.textContent = formatBytes(totalBytes);
    inspectUserItemsCount.textContent = userFiles.length;

    if (userFiles.length === 0) {
      inspectUserMediaTableBody.innerHTML = `<tr><td colspan="5" class="loading-state">No uploaded recordings or media found for this user.</td></tr>`;
      return;
    }

    inspectUserMediaTableBody.innerHTML = userFiles.map((file, idx) => `
      <tr>
        <td>
          <div style="font-weight: 500; word-break: break-word;">${file.fileName}</div>
          <span style="font-size: 0.75rem; color: var(--text-muted);">${file.mediaId}</span>
        </td>
        <td>
          <span class="badge ${file.mediaType === 'PHOTO' ? 'badge-success' : 'badge-primary'}">${file.mediaType}</span>
        </td>
        <td>${formatBytes(file.fileSize)}</td>
        <td>${formatDate(file.createdAt)}</td>
        <td style="text-align: right; white-space: nowrap;">
          ${file.downloadUrl ? `
            <button class="btn btn-secondary btn-sm" onclick="window.previewUserFile(${idx})">👁️ Preview</button>
            <a href="${file.downloadUrl}" target="_blank" download class="btn btn-secondary btn-sm" style="text-decoration: none;">📥 Download</a>
          ` : `<span class="badge badge-guest">No URL</span>`}
          <button class="btn btn-danger btn-sm" onclick="window.deleteUserFilePrompt('${userId}', '${file.id}', '${file.source}', '${file.fileName.replace(/'/g, "\\'")}')">🗑️ Delete</button>
        </td>
      </tr>
    `).join("");

  } catch (err) {
    console.error("Error inspecting user:", err);
    showToast("Error inspecting user: " + err.message);
  }
};

window.previewUserFile = function(fileIndex) {
  const file = currentInspectedUserFiles[fileIndex];
  if (!file) return;

  activePreviewItem = file;
  modalVideoTitle.textContent = file.fileName || "User Media Preview";
  modalVideoSize.textContent = "Size: " + formatBytes(file.fileSize);
  modalVideoDuration.textContent = file.duration ? "Duration: " + formatDuration(file.duration) : "";
  modalVideoDevice.textContent = "User: " + (currentInspectedUser?.email || "Registered User");
  modalDownloadBtn.href = file.downloadUrl || "#";
  modalDownloadBtn.setAttribute("download", file.fileName || "media.mp4");

  videoPlayer.src = file.downloadUrl || "";
  videoModal.classList.remove("hidden");
};

window.deleteUserFilePrompt = async function(userId, fileId, source, fileName) {
  if (!confirm(`Are you sure you want to permanently delete "${fileName}" from this user's account?`)) return;

  try {
    if (source === "vault_media") {
      await deleteDoc(doc(db, "users", userId, "vault_media", fileId));
    } else if (source === "recordings") {
      await deleteDoc(doc(db, "users", userId, "recordings", fileId));
    } else if (source === "cloud_recordings") {
      await deleteDoc(doc(db, "cloud_recordings", fileId));
    }

    try {
      await deleteDoc(doc(db, "cloud_recordings", `user_${userId}_${fileId}`));
    } catch (_) {}

    showToast(`Deleted ${fileName} successfully!`);
    recordAuditLog("DELETE_USER_MEDIA", `${userId}/${fileName}`, { source, fileId });
    await window.inspectUser(userId);
    await loadRecordings();
  } catch (err) {
    showToast("Error deleting file: " + err.message);
  }
};

window.toggleUserPremium = async function(userId, makePremium) {
  try {
    await setDoc(doc(db, "users", userId), {
      isPremium: makePremium,
      premiumGrantedByAdmin: makePremium,
      updatedAt: serverTimestamp()
    }, { merge: true });

    showToast(`User premium updated to: ${makePremium ? 'PREMIUM' : 'FREE'}`);
    recordAuditLog(makePremium ? "GRANT_PREMIUM" : "REVOKE_PREMIUM", userId);
    await loadUsers();
  } catch (err) {
    showToast("Error updating user premium: " + err.message);
  }
};

window.toggleUserBan = async function(userId, status) {
  try {
    await setDoc(doc(db, "users", userId), {
      accountStatus: status,
      updatedAt: serverTimestamp()
    }, { merge: true });

    showToast(`User status set to ${status}`);
    recordAuditLog(`SET_STATUS_${status}`, userId);
    await loadUsers();
  } catch (err) {
    showToast("Error setting user status: " + err.message);
  }
};

refreshUsersBtn.addEventListener("click", () => {
  loadUsers();
  showToast("User list refreshed.");
});

// --- 5. Storage Management & Cleanup ---
function renderStorageMetrics(list) {
  let totalBytes = 0;
  list.forEach((r) => totalBytes += Number(r.fileSize || r.sizeBytes || 0));

  storageUsedDisplay.textContent = formatBytes(totalBytes);
  const remaining = Math.max(0, FREE_TIER_BYTES_LIMIT - totalBytes);
  storageFreeDisplay.textContent = formatBytes(remaining);
}

cleanOldestBtn.addEventListener("click", async () => {
  const guests = recordings.filter((r) => (r.ownerType || "GUEST") === "GUEST");
  if (guests.length === 0) return showToast("No guest recordings found to clean.");

  const oldest = guests.slice(-10);
  if (!confirm(`Delete ${oldest.length} oldest guest recordings to free space?`)) return;

  showToast(`Deleting ${oldest.length} oldest files...`);
  for (const item of oldest) {
    await deleteSingleRecordingInternal(item);
  }
  showToast(`Cleaned ${oldest.length} oldest recordings!`);
  await loadRecordings();
});

cleanLargestBtn.addEventListener("click", async () => {
  const large = recordings.filter((r) => (Number(r.fileSize || r.sizeBytes) || 0) > 30 * 1024 * 1024);
  if (large.length === 0) return showToast("No large recordings (>30MB) found.");

  if (!confirm(`Delete ${large.length} large recordings (>30MB) to recover space?`)) return;
  for (const item of large) {
    await deleteSingleRecordingInternal(item);
  }
  showToast(`Cleaned ${large.length} large recordings!`);
  await loadRecordings();
});

cleanAllGuestBtn.addEventListener("click", () => {
  confirmPurgeModal.classList.remove("hidden");
});

quickPurgeBtn.addEventListener("click", () => {
  confirmPurgeModal.classList.remove("hidden");
});

recPurgeAllBtn.addEventListener("click", () => {
  confirmPurgeModal.classList.remove("hidden");
});

confirmPurgeCloseBtn.addEventListener("click", () => confirmPurgeModal.classList.add("hidden"));
cancelPurgeBtn.addEventListener("click", () => confirmPurgeModal.classList.add("hidden"));

executePurgeBtn.addEventListener("click", async () => {
  confirmPurgeModal.classList.add("hidden");
  showToast("Purging all guest cloud recordings...");
  executePurgeBtn.disabled = true;

  try {
    let deletedCount = 0;
    for (const item of recordings) {
      await deleteSingleRecordingInternal(item);
      deletedCount++;
    }
    showToast(`Purged ${deletedCount} recordings! 100% of space freed.`);
    recordAuditLog("PURGE_ALL_RECORDINGS", "Firebase Storage", { count: deletedCount });
    await loadRecordings();
  } catch (err) {
    showToast("Purge failed: " + err.message);
  } finally {
    executePurgeBtn.disabled = false;
  }
});

// --- 6. Deletion Functions ---
async function deleteSingleRecordingInternal(item) {
  const path = item.cloudStoragePath || item.storagePath;
  if (path) {
    try {
      await deleteObject(ref(storage, path));
    } catch (_) {}
  }
  await deleteDoc(doc(db, "cloud_recordings", item.id));
}

window.deleteRecordingPrompt = async function(id) {
  const item = recordings.find((r) => r.id === id);
  if (!item) return;

  if (confirm(`Permanently delete "${item.fileName || id}" from Cloud Storage?`)) {
    showToast("Deleting file...");
    await deleteSingleRecordingInternal(item);
    recordAuditLog("DELETE_RECORDING", id, { fileName: item.fileName });
    showToast("Deleted successfully.");
    await loadRecordings();
  }
};

recDeleteSelectedBtn.addEventListener("click", async () => {
  const ids = Array.from(selectedRecordingsIds);
  if (ids.length === 0) return;

  if (!confirm(`Delete all ${ids.length} selected recordings?`)) return;

  showToast(`Deleting ${ids.length} recordings...`);
  for (const id of ids) {
    const item = recordings.find((r) => r.id === id);
    if (item) await deleteSingleRecordingInternal(item);
  }
  recordAuditLog("BULK_DELETE_RECORDINGS", `${ids.length} items`, { count: ids.length });
  showToast(`Deleted ${ids.length} recordings.`);
  await loadRecordings();
});

// --- 7. Video Preview ---
window.previewRecording = function(id) {
  const item = recordings.find((r) => r.id === id);
  if (!item) return;

  activePreviewItem = item;
  modalVideoTitle.textContent = item.fileName || "Cloud Recording";
  modalVideoSize.textContent = "Size: " + formatBytes(item.fileSize || item.sizeBytes);
  modalVideoDuration.textContent = "Duration: " + formatDuration(item.duration || item.durationMs);
  modalVideoDevice.textContent = "Device: " + (item.deviceModel || item.deviceId || "Guest");
  modalDownloadBtn.href = item.downloadUrl || "#";
  modalDownloadBtn.setAttribute("download", item.fileName || "recording.mp4");

  videoPlayer.src = item.downloadUrl || "";
  videoModal.classList.remove("hidden");
};

modalCloseBtn.addEventListener("click", () => {
  videoPlayer.pause();
  videoPlayer.src = "";
  videoModal.classList.add("hidden");
  activePreviewItem = null;
});

modalDeleteBtn.addEventListener("click", async () => {
  if (activePreviewItem) {
    const item = activePreviewItem;
    modalCloseBtn.click();
    await window.deleteRecordingPrompt(item.id);
  }
});

// --- 8. Audit Logs ---
async function recordAuditLog(action, target, metadata = {}) {
  try {
    const adminIdentity = sessionStorage.getItem("admin_identity") || (currentAdminUser ? currentAdminUser.email : "Super Admin");
    await addDoc(collection(db, "admin_audit_logs"), {
      adminIdentity,
      action,
      target,
      metadata,
      createdAt: Date.now()
    });
  } catch (err) {
    console.warn("Could not save audit log:", err);
  }
}

async function loadAuditLogs() {
  try {
    const snap = await getDocs(query(collection(db, "admin_audit_logs"), limit(50)));
    const items = [];
    snap.forEach((d) => items.push({ id: d.id, ...d.data() }));
    items.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
    auditLogs = items;
    renderAuditLogsTable(auditLogs);
  } catch (err) {
    auditTableBody.innerHTML = `<tr><td colspan="5" class="loading-state">Audit logs will appear as admin actions are performed.</td></tr>`;
  }
}

function renderAuditLogsTable(list) {
  if (list.length === 0) {
    auditTableBody.innerHTML = `<tr><td colspan="5" class="loading-state">No administrative actions logged yet.</td></tr>`;
    return;
  }

  auditTableBody.innerHTML = list.map((log) => `
    <tr>
      <td>${formatDate(log.createdAt)}</td>
      <td><strong>${log.adminIdentity || 'Super Admin'}</strong></td>
      <td><span class="badge badge-primary">${log.action}</span></td>
      <td><code>${log.target || '--'}</code></td>
      <td><span class="badge badge-success">SUCCESS</span></td>
    </tr>
  `).join("");
}

refreshAuditBtn.addEventListener("click", () => {
  loadAuditLogs();
  showToast("Audit logs updated.");
});

// Toast helper
function showToast(msg) {
  toast.textContent = msg;
  toast.classList.remove("hidden");
  clearTimeout(window.__toastTimeout);
  window.__toastTimeout = setTimeout(() => toast.classList.add("hidden"), 3500);
}

// --- 9. License Keys Management (No-Gmail Premium) ---
async function loadLicenseKeys() {
  try {
    const q = query(collection(db, "license_keys"));
    const snap = await getDocs(q);
    const items = [];
    snap.forEach((d) => items.push({ id: d.id, ...d.data() }));
    items.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
    licenseKeys = items;

    renderLicenseKeysTable(licenseKeys);
    updateLicenseStats(licenseKeys);
  } catch (err) {
    console.error("Error fetching license keys:", err);
    if (licenseKeysTableBody) {
      licenseKeysTableBody.innerHTML = `<tr><td colspan="7" class="loading-state">Error loading license keys. Check permissions.</td></tr>`;
    }
  }
}

function updateLicenseStats(keys) {
  if (licTotalKeys) licTotalKeys.textContent = keys.length;
  if (sidebarLicensesBadge) sidebarLicensesBadge.textContent = keys.length;
  const activeCount = keys.filter(k => k.isRedeemed && k.status !== "REVOKED").length;
  const availableCount = keys.filter(k => !k.isRedeemed && k.status !== "REVOKED").length;
  if (licActiveKeys) licActiveKeys.textContent = activeCount;
  if (licAvailableKeys) licAvailableKeys.textContent = availableCount;
}

function renderLicenseKeysTable(keys) {
  if (!licenseKeysTableBody) return;
  if (keys.length === 0) {
    licenseKeysTableBody.innerHTML = `<tr><td colspan="7" class="loading-state">No license keys generated yet. Use the form above to generate an activation code.</td></tr>`;
    return;
  }

  licenseKeysTableBody.innerHTML = keys.map((key) => {
    const isRevoked = key.status === "REVOKED";
    const statusBadge = isRevoked 
      ? `<span class="badge badge-danger">REVOKED</span>` 
      : key.isRedeemed 
        ? `<span class="badge badge-success">REDEEMED</span>` 
        : `<span class="badge badge-warning">AVAILABLE</span>`;

    const planBadgeClass = key.planType === "LIFETIME" ? "badge-primary" : key.planType === "YEARLY" ? "badge-success" : "badge-outline";
    const expiryText = key.planType === "LIFETIME" ? "Never (Lifetime)" : (key.expiresAt ? formatDate(key.expiresAt) : `${key.validityDays || 30} Days`);

    return `
      <tr>
        <td><strong style="font-family: var(--font-mono); color: #60a5fa;">${key.code || key.id}</strong></td>
        <td><span class="badge ${planBadgeClass}">${key.planType || 'LIFETIME'}</span></td>
        <td>${statusBadge}</td>
        <td>${key.customerNote || '<span style="color: #64748b;">--</span>'}</td>
        <td><code>${key.redeemedByDeviceId || key.redeemedByUid || 'Not Claimed'}</code></td>
        <td>${expiryText}</td>
        <td style="text-align: right;">
          <div style="display: flex; gap: 6px; justify-content: flex-end;">
            <button class="btn btn-secondary btn-sm" onclick="window.copyLicenseKey('${key.code || key.id}')">Copy</button>
            ${!isRevoked ? `<button class="btn btn-warning btn-sm" onclick="window.revokeLicenseKeyPrompt('${key.code || key.id}')">Revoke</button>` : ''}
            <button class="btn btn-danger btn-sm" onclick="window.deleteLicenseKeyPrompt('${key.code || key.id}')">Delete</button>
          </div>
        </td>
      </tr>
    `;
  }).join("");
}

// Global window actions for License Keys
window.copyLicenseKey = (code) => {
  navigator.clipboard.writeText(code).then(() => {
    showToast(`Key copied: ${code}`);
  });
};

window.revokeLicenseKeyPrompt = async (code) => {
  if (!confirm(`Are you sure you want to revoke license key ${code}? The user will immediately lose Premium access.`)) return;
  try {
    await setDoc(doc(db, "license_keys", code), { status: "REVOKED", revokedAt: Date.now() }, { merge: true });
    await recordAuditLog("REVOKE_LICENSE_KEY", code);
    showToast(`Revoked key ${code}`);
    loadLicenseKeys();
  } catch (e) {
    alert("Error revoking key: " + e.message);
  }
};

window.deleteLicenseKeyPrompt = async (code) => {
  if (!confirm(`Permanently delete license key record ${code}?`)) return;
  try {
    await deleteDoc(doc(db, "license_keys", code));
    await recordAuditLog("DELETE_LICENSE_KEY", code);
    showToast(`Deleted key ${code}`);
    loadLicenseKeys();
  } catch (e) {
    alert("Error deleting key: " + e.message);
  }
};

// Generate Key Form Handler
if (generateKeyForm) {
  generateKeyForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const plan = keyPlanSelect.value;
    const note = keyCustomerInput.value.trim();

    // Random hex generator e.g. VAULT-LIFE-A48B-9321
    const rand1 = Math.random().toString(36).substring(2, 6).toUpperCase();
    const rand2 = Math.random().toString(36).substring(2, 6).toUpperCase();
    const prefix = plan === "LIFETIME" ? "LIFE" : plan === "YEARLY" ? "1YR" : "30D";
    const generatedCode = `VAULT-${prefix}-${rand1}-${rand2}`;

    const validityDays = plan === "LIFETIME" ? 99999 : plan === "YEARLY" ? 365 : 30;

    try {
      await setDoc(doc(db, "license_keys", generatedCode), {
        code: generatedCode,
        planType: plan,
        validityDays: validityDays,
        customerNote: note,
        isRedeemed: false,
        status: "ACTIVE",
        createdAt: Date.now()
      });

      await recordAuditLog("GENERATE_LICENSE_KEY", generatedCode, { plan, note });

      // Show alert banner
      if (newKeyCodeText) newKeyCodeText.textContent = generatedCode;
      if (newKeyPlanText) newKeyPlanText.textContent = `${plan} Premium Activation Key`;
      if (newKeyAlert) newKeyAlert.classList.remove("hidden");

      if (btnCopyKey) {
        btnCopyKey.onclick = () => {
          navigator.clipboard.writeText(generatedCode).then(() => showToast("Copied: " + generatedCode));
        };
      }

      if (btnCopyWhatsAppMsg) {
        btnCopyWhatsAppMsg.onclick = () => {
          const msg = `🌟 *Calculator Vault Premium Activation*\n\nHello! Here is your official Premium Activation Key:\n👉 *${generatedCode}*\n\nPlan: *${plan} Premium*\n\n*How to Activate:*\n1. Open Calculator Vault app on your mobile.\n2. Tap Settings -> Premium.\n3. Scroll to *Redeem Activation Code*.\n4. Paste this code and tap *Activate Key*.\n\nEnjoy unlimited cloud vault privileges! (Save this code; works even if you reinstall).`;
          navigator.clipboard.writeText(msg).then(() => showToast("WhatsApp text copied!"));
        };
      }

      keyCustomerInput.value = "";
      showToast("License key created successfully!");
      loadLicenseKeys();
    } catch (err) {
      alert("Error generating license key: " + err.message);
    }
  });
}

if (refreshKeysBtn) {
  refreshKeysBtn.addEventListener("click", () => {
    loadLicenseKeys();
    showToast("License keys refreshed.");
  });
}

// --- 10. Dynamic Limits Remote Config ---
async function loadDynamicLimits() {
  try {
    const docSnap = await getDoc(doc(db, "system_config", "app_limits"));
    if (docSnap.exists()) {
      const data = docSnap.data();
      if (limitFreeRecordings && data.freeMaxRecordings !== undefined) {
        limitFreeRecordings.value = data.freeMaxRecordings;
      }
      if (limitFreeStorageMb && data.freeStorageLimitBytes !== undefined) {
        limitFreeStorageMb.value = Math.round(data.freeStorageLimitBytes / (1024 * 1024));
      }
      if (limitPremiumStorageGb && data.premiumStorageLimitBytes !== undefined) {
        limitPremiumStorageGb.value = Math.round(data.premiumStorageLimitBytes / (1024 * 1024 * 1024));
      }
    }
  } catch (err) {
    console.warn("Could not load dynamic limits:", err);
  }
}

if (dynamicLimitsForm) {
  dynamicLimitsForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const freeRec = parseInt(limitFreeRecordings.value, 10) || 5;
    const freeMb = parseInt(limitFreeStorageMb.value, 10) || 500;
    const premGb = parseInt(limitPremiumStorageGb.value, 10) || 50;

    const payload = {
      freeMaxRecordings: freeRec,
      freeStorageLimitBytes: freeMb * 1024 * 1024,
      premiumMaxRecordings: 1000,
      premiumStorageLimitBytes: premGb * 1024 * 1024 * 1024,
      updatedAt: Date.now()
    };

    try {
      await setDoc(doc(db, "system_config", "app_limits"), payload, { merge: true });
      await recordAuditLog("UPDATE_DYNAMIC_LIMITS", "system_config/app_limits", payload);
      showToast(`Saved! Free limit: ${freeRec} recordings & ${freeMb} MB.`);
    } catch (err) {
      alert("Error saving quota limits: " + err.message);
    }
  });
}

// --- 11. Multi-Account Cloudinary Remote Config (Up to 5 Accounts) ---
async function loadCloudinaryConfig() {
  try {
    const docSnap = await getDoc(doc(db, "system_config", "cloudinary"));
    if (docSnap.exists()) {
      const data = docSnap.data();
      const accounts = Array.isArray(data.accounts) ? data.accounts : [];

      for (let i = 0; i < 5; i++) {
        const cNameEl = document.getElementById(`cld_cloudName_${i}`);
        const presetEl = document.getElementById(`cld_preset_${i}`);
        const labelEl = document.getElementById(`cld_label_${i}`);
        const enabledEl = document.getElementById(`cld_enabled_${i}`);

        if (accounts[i]) {
          if (cNameEl) cNameEl.value = accounts[i].cloudName || "";
          if (presetEl) presetEl.value = accounts[i].uploadPreset || "";
          if (labelEl) labelEl.value = accounts[i].label || "";
          if (enabledEl) enabledEl.checked = accounts[i].enabled !== false;
        } else if (i === 0 && data.cloudName) {
          // Backward compatibility for legacy single-account document
          if (cNameEl) cNameEl.value = data.cloudName || "";
          if (presetEl) presetEl.value = data.uploadPreset || "";
          if (labelEl) labelEl.value = "Primary Account";
          if (enabledEl) enabledEl.checked = true;
        }
      }
    }
  } catch (err) {
    console.warn("Could not load Cloudinary config pool:", err);
  }
}

if (cloudinaryConfigForm) {
  cloudinaryConfigForm.addEventListener("submit", async (e) => {
    e.preventDefault();

    const accounts = [];
    for (let i = 0; i < 5; i++) {
      const cNameEl = document.getElementById(`cld_cloudName_${i}`);
      const presetEl = document.getElementById(`cld_preset_${i}`);
      const labelEl = document.getElementById(`cld_label_${i}`);
      const enabledEl = document.getElementById(`cld_enabled_${i}`);

      const cloudName = cNameEl ? cNameEl.value.trim() : "";
      const uploadPreset = presetEl ? presetEl.value.trim() : "";
      const label = labelEl ? labelEl.value.trim() : `Account ${i + 1}`;
      const enabled = enabledEl ? enabledEl.checked : false;

      if (cloudName && uploadPreset) {
        accounts.push({
          id: `acc_${i + 1}`,
          index: i,
          cloudName,
          uploadPreset,
          label: label || `Account ${i + 1}`,
          enabled: enabled
        });
      }
    }

    if (accounts.length === 0) {
      alert("Please enter at least Account 1 Cloud Name and Upload Preset.");
      return;
    }

    // First active account becomes primary for backward compatibility
    const primary = accounts.find(a => a.enabled) || accounts[0];

    const payload = {
      // Legacy fields for backward compatibility with older app versions
      cloudName: primary.cloudName,
      uploadPreset: primary.uploadPreset,
      isActive: true,

      // Multi-account pool for new resilient app versions
      accounts: accounts,
      rotationMode: "AUTO_FAILOVER", // Android app tries active accounts sequentially
      totalAccounts: accounts.length,
      updatedAt: Date.now()
    };

    try {
      await setDoc(doc(db, "system_config", "cloudinary"), payload, { merge: true });
      await recordAuditLog("UPDATE_CLOUDINARY_POOL", `${accounts.length} Accounts Configured`, {
        accounts: accounts.map(a => `${a.label} (${a.cloudName}) - ${a.enabled ? 'Active' : 'Disabled'}`)
      });
      showToast(`Saved! ${accounts.length} Cloudinary accounts synced with Auto-Failover.`);
    } catch (err) {
      alert("Error saving Cloudinary config pool: " + err.message);
    }
  });
}

