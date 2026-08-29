const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const mongoose = require('mongoose');
const cors = require('cors');
require('dotenv').config();

const admin = require('firebase-admin');

const Message = require('./models/Message');
const DeviceToken = require('./models/DeviceToken');

const app = express();
app.use(cors());
app.use(express.json());

const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: "*" }
});

// تهيئة firebase-admin
if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
  admin.initializeApp({
    credential: admin.credential.applicationDefault()
  });
} else {
  // للتطوير محليًا: ضع ملف serviceAccountKey.json في مجلد الخادم (لا ترفعه للمستودع)
  try {
    const serviceAccount = require('./serviceAccountKey.json');
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
  } catch (e) {
    console.warn('لم يتم العثور على serviceAccountKey.json و GOOGLE_APPLICATION_CREDENTIALS غير معرف. FCM غير مفعّل.');
  }
}

const onlineUsers = new Map();

mongoose.connect(process.env.MONGO_URI || 'mongodb://localhost:27017/twasol_chat', {
  useNewUrlParser: true,
  useUnifiedTopology: true
}).then(() => console.log('تم الاتصال بقاعدة البيانات بنجاح')).catch(err => console.log(err));

// مسار لتسجيل device token
app.post('/register-token', async (req, res) => {
  try {
    const { userId, token } = req.body;
    if (!userId || !token) return res.status(400).json({ error: 'userId و token مطلوبان' });

    await DeviceToken.findOneAndUpdate(
      { userId },
      { token, updatedAt: new Date() },
      { upsert: true, new: true }
    );

    res.json({ ok: true });
  } catch (err) {
    console.error('register-token error', err);
    res.status(500).json({ error: 'server error' });
  }
});

app.get('/messages', async (req, res) => {
  try {
    const { user1, user2, limit = 50, skip = 0 } = req.query;
    if (!user1 || !user2) return res.status(400).json({ error: 'user1 و user2 مطلوبان' });

    const messages = await Message.find({
      $or: [
        { senderId: user1, receiverId: user2 },
        { senderId: user2, receiverId: user1 }
      ]
    })
      .sort({ timestamp: -1 })
      .skip(parseInt(skip))
      .limit(parseInt(limit));

    res.json(messages.reverse());
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'خطأ في السيرفر' });
  }
});

app.post('/messages/mark-read', async (req, res) => {
  try {
    const { userId, otherId } = req.body;
    if (!userId || !otherId) return res.status(400).json({ error: 'userId و otherId مطلوبان' });

    const result = await Message.updateMany(
      { senderId: otherId, receiverId: userId, isRead: false },
      { $set: { isRead: true } }
    );
    res.json({ modifiedCount: result.modifiedCount });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'خطأ في السيرفر' });
  }
});

io.on('connection', (socket) => {
  console.log(`مستخدم متصل: ${socket.id}`);

  socket.on('join_room', (userId) => {
    try {
      onlineUsers.set(userId, socket.id);
      socket.join(userId);
      console.log(`المستخدم ${userId} انضم للغرفة`);
    } catch (err) {
      console.error('join_room error', err);
    }
  });

  socket.on('send_message', async (data) => {
    try {
      const newMessage = new Message({
        senderId: data.senderId,
        receiverId: data.receiverId,
        content: data.content,
        timestamp: data.timestamp ? new Date(data.timestamp) : Date.now()
      });
      const saved = await newMessage.save();

      const receiverSocketId = onlineUsers.get(data.receiverId);
      if (receiverSocketId) {
        io.to(receiverSocketId).emit('receive_message', saved);
      } else {
        // إذا المستلم غير متّصل: حاول إرسال FCM
        try {
          const device = await DeviceToken.findOne({ userId: data.receiverId });
          if (device && device.token && admin.apps.length > 0) {
            const payload = {
              notification: {
                title: 'رسالة جديدة',
                body: `${data.content}`,
                sound: 'default'
              },
              data: {
                senderId: data.senderId,
                receiverId: data.receiverId,
                content: data.content,
                messageId: saved._id.toString(),
                timestamp: saved.timestamp.toISOString()
              }
            };

            admin.messaging().sendToDevice(device.token, payload)
              .then(response => {
                console.log('FCM sent:', response);
              })
              .catch(err => {
                console.error('FCM error:', err);
                // إذا فشل بسبب token غير صالح، يمكن حذف token هنا
              });
          } else {
            console.log(`لا يوجد device token صالح للمستخدم ${data.receiverId} أو firebase غير مهيأ.`);
          }
        } catch (err) {
          console.error('خطأ عند محاولة إرسال FCM:', err);
        }
      }

      socket.emit('message_sent', saved);
    } catch (error) {
      console.error('خطأ في حفظ الرسالة:', error);
      socket.emit('error_saving_message', { error: 'failed to save' });
    }
  });

  socket.on('disconnect', () => {
    for (const [userId, sId] of onlineUsers.entries()) {
      if (sId === socket.id) {
        onlineUsers.delete(userId);
        console.log(`تمت إزالة ${userId} من onlineUsers`);
        break;
      }
    }
    console.log(`انقطع الاتصال: ${socket.id}`);
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`خادم Twasol Chat يعمل بكفاءة على المنفذ ${PORT}`);
});
