'use strict';

const { createHash, randomUUID } = require('node:crypto');
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

  async reserve(userId, mealId, requestHash) {
    const reference = this.firestore.collection('meals').doc(mealDocumentId({ userId, mealId }));
    const reservationId = randomUUID();
    return this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (snapshot.exists && snapshot.get('record')) {
        if (snapshot.get('requestHash') !== requestHash) {
          return { type: 'different' };
        }
        return { type: 'saved', savedMeal: { record: snapshot.get('record'), status: snapshot.get('status') } };
      }
      if (snapshot.exists && snapshot.get('requestHash') !== requestHash) return { type: 'different' };
      if (snapshot.exists && isReservationActive(snapshot.get('analysisStartedAt'))) return { type: 'active' };
      const reservation = { type: 'acquired', userId, mealId, requestHash, reservationId };
      if (snapshot.exists) {
        transaction.update(reference, reservationFields(reservation));
      } else {
        transaction.create(reference, { ...reservationFields(reservation), status: 'analyzing' });
      }
      return reservation;
    });
  }

  async saveReserved(record, reservation) {
    const reference = this.firestore.collection('meals').doc(mealDocumentId(record));
    const contentHash = createHash('sha256').update(JSON.stringify(record)).digest('hex');
    return this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (!isReservationOwner(snapshot, reservation)) return false;
      transaction.update(reference, {
        record,
        contentHash,
        status: 'pending',
        submittedAt: new Date().toISOString(),
      });
      return true;
    });
  }

  async releaseReservation(reservation) {
    const reference = this.firestore.collection('meals').doc(mealDocumentId(reservation));
    await this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (isReservationOwner(snapshot, reservation)) transaction.delete(reference);
    });
  }

  async saveIfAbsent(record) {
    const reference = this.firestore.collection('meals').doc(mealDocumentId(record));
    const contentHash = createHash('sha256').update(JSON.stringify(record)).digest('hex');
    return this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (snapshot.exists) return snapshot.get('contentHash') === contentHash ? snapshot.get('status') : 'different';
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

function isReservationActive(analysisStartedAt) {
  const startedAt = Date.parse(analysisStartedAt);
  return Number.isFinite(startedAt) && Date.now() - startedAt < ANALYSIS_RESERVATION_DURATION_MILLIS;
}

function reservationFields(reservation) {
  return {
    requestHash: reservation.requestHash,
    reservationId: reservation.reservationId,
    analysisStartedAt: new Date().toISOString(),
  };
}

function isReservationOwner(snapshot, reservation) {
  return snapshot.exists && !snapshot.get('record') &&
    snapshot.get('requestHash') === reservation.requestHash &&
    snapshot.get('reservationId') === reservation.reservationId;
}

module.exports = { FirestoreMealRepository };
