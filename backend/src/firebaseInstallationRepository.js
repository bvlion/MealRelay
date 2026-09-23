'use strict';

const { createHash } = require('node:crypto');

function documentId(value) {
  return createHash('sha256').update(value).digest('hex');
}

class FirebaseInstallationRepository {
  constructor(firestore) {
    this.firestore = firestore;
  }

  async register({ sub, fid, previousFid }) {
    const reference = (value) => this.firestore.collection('firebaseInstallations').doc(documentId(value));
    const currentReference = reference(fid);
    const previousReference = previousFid && previousFid !== fid ? reference(previousFid) : null;
    await this.firestore.runTransaction(async (transaction) => {
      const current = await transaction.get(currentReference);
      if (current.exists && current.get('sub') !== sub) {
        throw new Error('Firebase installation belongs to another user');
      }
      if (previousReference) {
        const previous = await transaction.get(previousReference);
        if (previous.exists && previous.get('sub') === sub) transaction.delete(previousReference);
      }
      transaction.set(currentReference, { sub, fid, isActive: true });
    });
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
