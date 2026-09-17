# Vault Cloud Recording Web Admin Dashboard

A standalone Web Admin Dashboard built specifically for **`sleathcam1`** Firebase project to manage, view, preview, and delete recordings uploaded from guest Android devices without requiring paid plans.

---

## 🚀 How to Open the Dashboard

1. **Local Browser**:
   - Double-click or open `admin-portal/index.html` directly in any web browser (Chrome, Edge, Firefox, Safari).
   - Or run a local HTTP server:
     ```bash
     npx serve admin-portal
     # OR
     python3 -m http.server 8080 -d admin-portal
     ```
2. **Online Hosting**:
   - You can upload or deploy the `admin-portal` folder directly to **Firebase Hosting** (`firebase deploy --only hosting`), **GitHub Pages**, or **Vercel** / **Netlify**.

---

## 🔑 Access Credentials

- **Default Admin Security PIN**: `1234`
  - When opening the dashboard, enter `1234` to unlock the console.
  - You can lock the dashboard at any time using the "Lock" button in the top navigation bar.

---

## ⚡ Features Included

1. **Live Free Tier Storage Meter**:
   - Shows consumed storage vs. the 5 GB free tier limit.
   - Live color-coded quota gauge (Green, Orange, Red) showing exact free space remaining.
2. **Guest Devices & Recordings Counter**:
   - Counts unique guest device IDs and active quota sessions.
3. **Storage Free Space Actions (No Premium Needed)**:
   - **Single Delete**: Delete any individual video from both Firebase Storage and Firestore.
   - **Bulk Delete**: Check multiple items and delete them together.
   - **Free All Space (Purge)**: One-click purge with confirmation to permanently delete 100% of stored recordings and reclaim full storage space.
4. **HTML5 Video Player**:
   - Built-in video player modal to preview video recordings directly inside the browser.
   - Direct MP4 download button.
5. **Instant Search & Filter**:
   - Filter by file name, device model, or ID.
