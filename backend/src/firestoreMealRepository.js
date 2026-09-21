'use strict';

const { createHash } = require('node:crypto');
const { MealRecordError } = require('./mealRecord');
const { mealDocumentId } = require('./googleHealthNutrition');

const ANALYSIS_RESERVATION_DURATION_MILLIS = 5 * 60 * 1000;

class FirestoreMealRepository {
  constructor(firestore) {
    this.firestore = firestore;
  }

  async find(userId, mealId) {
    const reference = this.firestore.collection('meals').doc(mealDocumentId({ userId, mealId }));
    const snapshot = await reference.get();
    if (!snapshot.exists || !snapshot.get('record')) return null;
    return { record: snapshot.get('record'), status: snapshot.get('status') };
  }

  async acquire(userId, mealId, requestHash) {
    const reference = this.firestore.collection('meals').doc(mealDocumentId({ userId, mealId }));
    return this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (snapshot.exists && snapshot.get('record')) {
        if (snapshot.get('requestHash') && snapshot.get('requestHash') !== requestHash) {
          throw new MealRecordError('Meal ID already has different content', 409);
        }
        return { isNew: false, savedMeal: { record: snapshot.get('record'), status: snapshot.get('status') } };
      }
      if (snapshot.exists) {
        if (snapshot.get('requestHash') !== requestHash) {
          throw new MealRecordError('Meal ID already has different content', 409);
        }
        const analysisStartedAt = Date.parse(snapshot.get('analysisStartedAt'));
        if (Number.isFinite(analysisStartedAt) &&
            Date.now() - analysisStartedAt < ANALYSIS_RESERVATION_DURATION_MILLIS) {
          throw new MealRecordError('Meal is already being analyzed', 503);
        }
        transaction.update(reference, { analysisStartedAt: new Date().toISOString() });
        return { isNew: true };
      }
      transaction.create(reference, {
        requestHash,
        status: 'analyzing',
        analysisStartedAt: new Date().toISOString(),
      });
      return { isNew: true };
    });
  }

  async saveReserved(record, requestHash) {
    const reference = this.firestore.collection('meals').doc(mealDocumentId(record));
    const contentHash = createHash('sha256').update(JSON.stringify(record)).digest('hex');
    return this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (!snapshot.exists || snapshot.get('record') || snapshot.get('requestHash') !== requestHash) {
        throw new MealRecordError('Meal reservation is invalid', 409);
      }
      transaction.update(reference, {
        record,
        contentHash,
        status: 'pending',
        submittedAt: new Date().toISOString(),
      });
      return 'new';
    });
  }

  async releaseReservation(userId, mealId, requestHash) {
    const reference = this.firestore.collection('meals').doc(mealDocumentId({ userId, mealId }));
    await this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (snapshot.exists && !snapshot.get('record') && snapshot.get('requestHash') === requestHash) {
        transaction.delete(reference);
      }
    });
  }

  async saveIfAbsent(record) {
    const reference = this.firestore.collection('meals').doc(mealDocumentId(record));
    const contentHash = createHash('sha256').update(JSON.stringify(record)).digest('hex');
    return this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (snapshot.exists) {
        if (!snapshot.get('record')) {
          throw new MealRecordError('Meal is already being analyzed', 503);
        }
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
