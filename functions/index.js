const functions = require('firebase-functions');
const admin = require('firebase-admin');
const crypto = require('crypto');
const nodemailer = require('nodemailer');

admin.initializeApp();
const db = admin.firestore();

// Configure transporter using functions config: firebase functions:config:set smtp.host="..." smtp.port="..." smtp.user="..." smtp.pass="..."
const transporter = nodemailer.createTransport({
  host: functions.config().smtp?.host || 'localhost',
  port: functions.config().smtp?.port || 1025,
  secure: false,
  auth: {
    user: functions.config().smtp?.user || '',
    pass: functions.config().smtp?.pass || ''
  }
});

function generateToken() {
  return crypto.randomBytes(20).toString('hex');
}

exports.generateReset = functions.https.onCall(async (data, context) => {
  const email = (data && data.email) ? String(data.email).toLowerCase().trim() : null;
  if (!email) {
    throw new functions.https.HttpsError('invalid-argument', 'Email is required');
  }

  try {
    // Find user by email using Admin SDK
    const userRecord = await admin.auth().getUserByEmail(email);
    const uid = userRecord.uid;
    const token = generateToken();
    const now = admin.firestore.Timestamp.now();
    const expires = admin.firestore.Timestamp.fromMillis(Date.now() + 15 * 60 * 1000); // 15 minutes

    await db.collection('passwordResetTokens').doc(token).set({
      uid: uid,
      email: email,
      createdAt: now,
      expiresAt: expires,
      used: false,
      attempts: 0
    });

    // Send email with the token (simple plaintext email)
    const mailOptions = {
      from: `Smart Expense Tracker <no-reply@yourdomain.com>`,
      to: email,
      subject: 'Password reset token',
      text: `Use the following code in the app to reset your password: ${token}\nThis code will expire in 15 minutes.`
    };

    await transporter.sendMail(mailOptions);

    return { success: true };
  } catch (err) {
    console.error('generateReset error', err);
    throw new functions.https.HttpsError('internal', err.message || 'Failed to generate reset token');
  }
});

exports.confirmReset = functions.https.onCall(async (data, context) => {
  const token = data && data.token ? String(data.token) : null;
  const newPassword = data && data.newPassword ? String(data.newPassword) : null;
  if (!token || !newPassword) {
    throw new functions.https.HttpsError('invalid-argument', 'Token and newPassword are required');
  }
  if (newPassword.length < 6) {
    throw new functions.https.HttpsError('invalid-argument', 'Password must be at least 6 characters');
  }

  try {
    const docRef = db.collection('passwordResetTokens').doc(token);
    const doc = await docRef.get();
    if (!doc.exists) {
      throw new functions.https.HttpsError('not-found', 'Invalid token');
    }
    const dataDoc = doc.data();
    if (dataDoc.used) {
      throw new functions.https.HttpsError('failed-precondition', 'Token already used');
    }
    const now = admin.firestore.Timestamp.now();
    if (dataDoc.expiresAt.toMillis && dataDoc.expiresAt.toMillis() < Date.now()) {
      throw new functions.https.HttpsError('deadline-exceeded', 'Token expired');
    }

    const uid = dataDoc.uid;
    // Update Auth password
    await admin.auth().updateUser(uid, { password: newPassword });

    // Update app database to keep passwordHash and salt in sync
    const salt = require('crypto').randomBytes(16).toString('base64');
    const hash = require('crypto').pbkdf2Sync(newPassword, Buffer.from(salt, 'base64'), 10000, 32, 'sha1').toString('base64');
    await admin.database().ref(`users/${uid}/auth`).update({ salt: salt, passwordHash: hash });

    await docRef.update({ used: true, usedAt: now });

    return { success: true };
  } catch (err) {
    console.error('confirmReset error', err);
    if (err instanceof functions.https.HttpsError) throw err;
    throw new functions.https.HttpsError('internal', err.message || 'Failed to confirm reset');
  }
});

