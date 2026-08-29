const mongoose = require('mongoose');

const refreshTokenSchema = new mongoose.Schema({
  tokenId: { type: String, required: true, unique: true, index: true },
  userId: { type: String, required: true, index: true },
  revoked: { type: Boolean, default: false },
  expiresAt: { type: Date, required: true },
  createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('RefreshToken', refreshTokenSchema);
