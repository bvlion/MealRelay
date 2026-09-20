'use strict';

const { createHash } = require('node:crypto');

function documentId(value) {
  return createHash('sha256').update(value).digest('hex');
}

class FirestoreAuthRepository {
  constructor(firestore) {
    this.firestore = firestore;
  }

  async findUser(sub) {
    const snapshot = await this.firestore.collection('users').doc(documentId(sub)).get();
    return snapshot.exists ? { sub: snapshot.get('sub'), refreshToken: snapshot.get('refreshToken') } : null;
  }

  async registerNewUser({ sub, email, refreshToken, token, maximumUsers }) {
    const userReference = this.firestore.collection('users').doc(documentId(sub));
    const emailReference = this.firestore.collection('registeredEmails').doc(documentId(email));
    const registrationReference = this.firestore.collection('metadata').doc('registration');
    const tokenReference = this.firestore.collection('deviceTokens').doc(documentId(token));

    return this.firestore.runTransaction(async (transaction) => {
      if ((await transaction.get(userReference)).exists) return 'existing';
      if ((await transaction.get(emailReference)).exists) return 'emailClaimed';
      const registration = await transaction.get(registrationReference);
      const count = registration.exists ? registration.get('count') : 0;
      if (count >= maximumUsers) return 'full';

      if (registration.exists) {
        transaction.update(registrationReference, { count: count + 1 });
      } else {
        transaction.create(registrationReference, { count: 1 });
      }
      transaction.create(emailReference, { sub });
      transaction.create(userReference, { sub, refreshToken });
      transaction.create(tokenReference, { sub });
      return 'registered';
    });
  }

  async issueTokenForUser({ sub, refreshToken, token }) {
    const userReference = this.firestore.collection('users').doc(documentId(sub));
    const tokenReference = this.firestore.collection('deviceTokens').doc(documentId(token));
    return this.firestore.runTransaction(async (transaction) => {
      const user = await transaction.get(userReference);
      if (!user.exists || user.get('sub') !== sub) return false;
      if (refreshToken) transaction.update(userReference, { refreshToken });
      transaction.create(tokenReference, { sub });
      return true;
    });
  }

  async findSubByToken(token) {
    const snapshot = await this.firestore.collection('deviceTokens').doc(documentId(token)).get();
    return snapshot.exists ? snapshot.get('sub') : null;
  }

  async getRefreshToken(sub) {
    return (await this.findUser(sub))?.refreshToken;
  }
}

module.exports = { FirestoreAuthRepository };
