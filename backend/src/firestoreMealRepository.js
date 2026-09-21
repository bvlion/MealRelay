'use strict';

const { createHash } = require('node:crypto');
const { MealRecordError } = require('./mealRecord');
const { mealDocumentId } = require('./googleHealthNutrition');

class FirestoreMealRepository {
  constructor(firestore) {
    this.firestore = firestore;
  }

  async saveIfAbsent(record) {
    const reference = this.firestore.collection('meals').doc(mealDocumentId(record));
    const contentHash = createHash('sha256').update(JSON.stringify(record)).digest('hex');
    return this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (snapshot.exists) {
        if (snapshot.get('contentHash') !== contentHash) {
          throw new MealRecordError('Meal ID already has different content', 409);
        }
        return snapshot.get('status');
      }
      transaction.create(reference, {
        record,
        contentHash,
        status: 'pending',
        submittedAt: new Date().toISOString(),
      });
      return 'new';
    });
  }

  async markRegistered(record, googleHealthName) {
    const reference = this.firestore.collection('meals').doc(mealDocumentId(record));
    await reference.update({
      status: 'registered',
      googleHealthName,
      registeredAt: new Date().toISOString(),
    });
  }
}

module.exports = { FirestoreMealRepository };
