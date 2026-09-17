/**
 * Firebase Cloud Functions for Stealth Calculator Vault & Admin Control
 * - Super Admin Custom Claims verification
 * - Guest & Authenticated Cloud Recordings Management
 * - Storage Quota & Emergency Purge Operations
 * - User Administration & Premium Entitlement Management
 * - Immutable Security Audit Logging
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const cloudinary = require("cloudinary").v2;

admin.initializeApp();

const db = admin.firestore();
const storage = admin.storage();

// Configure Cloudinary
cloudinary.config({
  cloud_name: (functions.config().cloudinary && functions.config().cloudinary.cloud_name) || process.env.CLOUDINARY_CLOUD_NAME || "dp7j6mtdb",
  api_key: (functions.config().cloudinary && functions.config().cloudinary.api_key) || process.env.CLOUDINARY_API_KEY || "664263214121165",
  api_secret: (functions.config().cloudinary && functions.config().cloudinary.api_secret) || process.env.CLOUDINARY_API_SECRET || "g7g2zuBmJ5ChJUOeD7cbeAtvnTA",
  secure: true,
});

const SUPER_ADMIN_EMAILS = [
  "king.khan648k@gmail.com",
];

/**
 * Helper to verify caller has Super Admin privileges.
 */
function verifyAdmin(context) {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
  }
  const token = context.auth.token;
  const isSuperAdmin = token.role === "super_admin" || 
                       token.role === "ADMIN" || 
                       token.admin === true || 
                       (token.email && SUPER_ADMIN_EMAILS.includes(token.email.toLowerCase()));
  if (!isSuperAdmin) {
    throw new functions.https.HttpsError("permission-denied", "Super Admin access required.");
  }
  return token.email || context.auth.uid;
}

/**
 * Helper to write immutable Admin Audit Log.
 */
async function writeAuditLog(adminIdentity, action, target, metadata = {}) {
  try {
    await db.collection("admin_audit_logs").add({
      adminIdentity,
      action,
      target,
      metadata,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      createdAt: Date.now(),
    });
  } catch (err) {
    console.warn("Failed to write audit log:", err);
  }
}

/**
 * Callable Function: grantSuperAdminClaim
 * Sets custom claim for authorized email.
 */
exports.grantSuperAdminClaim = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
  }
  const callerEmail = (context.auth.token.email || "").toLowerCase();
  if (!SUPER_ADMIN_EMAILS.includes(callerEmail)) {
    throw new functions.https.HttpsError("permission-denied", "Unauthorized email for Super Admin.");
  }

  await admin.auth().setCustomUserClaims(context.auth.uid, {
    role: "super_admin",
    admin: true,
  });

  await writeAuditLog(callerEmail, "GRANT_SUPER_ADMIN_CLAIM", context.auth.uid);
  return { success: true, message: `Super Admin claims assigned to ${callerEmail}` };
});

/**
 * Callable Function: adminListGuestRecordings
 */
exports.adminListGuestRecordings = functions.https.onCall(async (data, context) => {
  verifyAdmin(context);
  const snapshot = await db.collection("cloud_recordings")
    .orderBy("createdAt", "desc")
    .limit(data.limit || 100)
    .get();

  const recordings = [];
  snapshot.forEach((doc) => {
    recordings.push({ id: doc.id, ...doc.data() });
  });

  return { success: true, count: recordings.length, recordings };
});

/**
 * Callable Function: adminDeleteCloudRecording
 */
exports.adminDeleteCloudRecording = functions.https.onCall(async (data, context) => {
  const adminId = verifyAdmin(context);
  const { recordingId, storagePath } = data;
  if (!recordingId) {
    throw new functions.https.HttpsError("invalid-argument", "recordingId is required.");
  }

  const docRef = db.collection("cloud_recordings").document(recordingId);
  const doc = await docRef.get();
  const recordingData = doc.exists ? doc.data() : null;

  const targetPath = storagePath || (recordingData && (recordingData.cloudStoragePath || recordingData.storagePath));
  if (targetPath) {
    try {
      const bucket = storage.bucket();
      await bucket.file(targetPath).delete();
    } catch (e) {
      console.warn(`Storage delete failed for ${targetPath}:`, e.message);
    }
  }

  await docRef.delete();
  await writeAuditLog(adminId, "DELETE_CLOUD_RECORDING", recordingId, { storagePath: targetPath });

  return { success: true, message: `Recording ${recordingId} deleted successfully.` };
});

/**
 * Callable Function: adminBulkDeleteCloudRecordings
 */
exports.adminBulkDeleteCloudRecordings = functions.https.onCall(async (data, context) => {
  const adminId = verifyAdmin(context);
  const { recordingIds } = data;
  if (!Array.isArray(recordingIds) || recordingIds.length === 0) {
    throw new functions.https.HttpsError("invalid-argument", "recordingIds array required.");
  }

  let deletedCount = 0;
  const bucket = storage.bucket();

  for (const recId of recordingIds) {
    try {
      const docRef = db.collection("cloud_recordings").document(recId);
      const doc = await docRef.get();
      if (doc.exists) {
        const d = doc.data();
        const p = d.cloudStoragePath || d.storagePath;
        if (p) {
          try { await bucket.file(p).delete(); } catch (_) {}
        }
        await docRef.delete();
        deletedCount++;
      }
    } catch (e) {
      console.error(`Error deleting ${recId}:`, e);
    }
  }

  await writeAuditLog(adminId, "BULK_DELETE_RECORDINGS", `${deletedCount} recordings`, { count: deletedCount });
  return { success: true, deletedCount };
});

/**
 * Callable Function: adminPurgeGuestRecordings
 * Purges all guest recordings to free space on Firebase Free Tier.
 */
exports.adminPurgeGuestRecordings = functions.https.onCall(async (data, context) => {
  const adminId = verifyAdmin(context);
  const snapshot = await db.collection("cloud_recordings")
    .where("ownerType", "==", "GUEST")
    .get();

  let purgedCount = 0;
  let freedBytes = 0;
  const bucket = storage.bucket();

  for (const doc of snapshot.docs) {
    const d = doc.data();
    const p = d.cloudStoragePath || d.storagePath;
    if (p) {
      try { await bucket.file(p).delete(); } catch (_) {}
    }
    freedBytes += (d.fileSize || d.sizeBytes || 0);
    await doc.ref.delete();
    purgedCount++;
  }

  await writeAuditLog(adminId, "PURGE_GUEST_RECORDINGS", `Purged ${purgedCount} guest recordings`, {
    purgedCount,
    freedBytes,
  });

  return { success: true, purgedCount, freedBytes };
});

/**
 * Callable Function: adminListUsers
 */
exports.adminListUsers = functions.https.onCall(async (data, context) => {
  verifyAdmin(context);
  const listUsersResult = await admin.auth().listUsers(100);
  const users = [];

  for (const u of listUsersResult.users) {
    let firestoreData = {};
    try {
      const uDoc = await db.collection("users").document(u.uid).get();
      if (uDoc.exists) firestoreData = uDoc.data();
    } catch (_) {}

    users.push({
      uid: u.uid,
      email: u.email || null,
      displayName: u.displayName || null,
      isAnonymous: u.providerData.length === 0,
      createdAt: u.metadata.creationTime,
      lastSignInTime: u.metadata.lastSignInTime,
      isPremium: firestoreData.isPremium || false,
      accountStatus: firestoreData.accountStatus || "ACTIVE",
    });
  }

  return { success: true, users };
});

/**
 * Callable Function: adminSetUserPremium
 */
exports.adminSetUserPremium = functions.https.onCall(async (data, context) => {
  const adminId = verifyAdmin(context);
  const { userId, isPremium } = data;
  if (!userId) throw new functions.https.HttpsError("invalid-argument", "userId required.");

  await db.collection("users").document(userId).set({
    isPremium: Boolean(isPremium),
    premiumGrantedByAdmin: Boolean(isPremium),
    premiumUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, { merge: true });

  await writeAuditLog(adminId, isPremium ? "GRANT_PREMIUM" : "REVOKE_PREMIUM", userId);
  return { success: true, isPremium };
});

/**
 * Callable Function: adminSetUserAccountStatus
 */
exports.adminSetUserAccountStatus = functions.https.onCall(async (data, context) => {
  const adminId = verifyAdmin(context);
  const { userId, status, reason } = data;
  if (!userId || !status) throw new functions.https.HttpsError("invalid-argument", "userId and status required.");

  await db.collection("users").document(userId).set({
    accountStatus: status,
    statusReason: reason || "",
    statusUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, { merge: true });

  await writeAuditLog(adminId, `SET_STATUS_${status}`, userId, { reason });
  return { success: true, status };
});

/**
 * Callable Function: generateCloudinaryUploadSignature
 */
exports.generateCloudinaryUploadSignature = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError("unauthenticated", "Auth required.");
  }
  const uid = context.auth.uid;
  const mediaId = data.mediaId;
  if (!mediaId) throw new functions.https.HttpsError("invalid-argument", "mediaId required.");

  const timestamp = Math.round(new Date().getTime() / 1000);
  const folder = `users/${uid}/vault`;
  const publicId = `media_${mediaId}`;

  const paramsToSign = {
    timestamp: timestamp,
    folder: folder,
    public_id: publicId,
  };

  const signature = cloudinary.utils.api_sign_request(paramsToSign, cloudinary.config().api_secret);
  return {
    signature,
    timestamp,
    apiKey: cloudinary.config().api_key,
    cloudName: cloudinary.config().cloud_name,
    folder,
    publicId,
  };
});
