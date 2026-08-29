const mongoose = require('mongoose');

const deviceTokenSchema = new mongoose.Schema({
  userId: { type: String, required: true, index: true, unique: true },
  token: { type: String, required: true },
  updatedAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('DeviceToken', deviceTokenSchema);
