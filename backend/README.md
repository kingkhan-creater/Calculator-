# Secure Cloudinary & Firebase Cloud Functions Backend Foundation

This backend foundation provides secure server-side upload signature generation and deletion synchronization for the Calculator Vault app.

## Setup Instructions

### 1. Cloudinary Dashboard Setup
1. Log in to your [Cloudinary Console](https://cloudinary.com/console).
2. Note your **Cloud Name**, **API Key**, and **API Secret**.

### 2. Firebase Cloud Functions Configuration
1. Initialize Firebase CLI in your local terminal if not already done:
   ```bash
   firebase login
   firebase use <your-firebase-project-id>
   ```
2. Navigate to the `backend/functions` directory:
   ```bash
   cd backend/functions
   npm install
   ```
3. Set your Cloudinary credentials securely in Firebase environment configuration (or Secrets Manager):
   ```bash
   firebase functions:config:set cloudinary.cloud_name="YOUR_CLOUD_NAME" cloudinary.api_key="YOUR_API_KEY" cloudinary.api_secret="YOUR_API_SECRET"
   ```
4. Deploy the Cloud Functions:
   ```bash
   firebase deploy --only functions
   ```

## Security Guarantees
- **No API Secret in App**: The Cloudinary API secret remains strictly on Firebase Cloud Functions.
- **Authenticated Signatures**: Every upload request requires a valid Firebase Auth ID token (`context.auth.uid`).
- **UID Isolation**: All uploaded assets are segregated strictly under `users/{uid}/vault`.
- **Firestore Metadata Only**: Large binary files never touch Firestore; only metadata and Cloudinary asset identifiers (`public_id`, `secure_url`) are stored.
