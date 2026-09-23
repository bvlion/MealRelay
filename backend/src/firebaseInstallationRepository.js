'use strict';

const { createHash } = require('node:crypto');

function documentId(value) {
  return createHash('sha256').update(value).digest('hex');
}

class FirebaseInstallationRepository {
  constructor(firestore) {
    this.firestore = firestore;
  }

  async register({ sub, fid }) {
    await this.firestore.collection('firebaseInstallations').doc(documentId(fid))
      .set({ sub, fid, isActive: true });
  }

  async findActiveByUser(sub) {
    const snapshot = await this.firestore.collection('firebaseInstallations')
      .where('sub', '==', sub).get();
    return snapshot.docs
      .filter((document) => document.get('isActive') === true)
      .map((document) => ({ id: document.id, fid: document.get('fid') }));
  }

  async deactivate(id) {
    await this.firestore.collection('firebaseInstallations').doc(id).update({ isActive: false });
  }
}

module.exports = { FirebaseInstallationRepository };
