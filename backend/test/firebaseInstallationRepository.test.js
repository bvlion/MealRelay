'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { createHash } = require('node:crypto');
const { FirebaseInstallationRepository } = require('../src/firebaseInstallationRepository');

test('reassigns an existing FID to the user authenticated by the current request', async () => {
  let document;
  let path;
  const firestore = {
    collection(name) {
      assert.equal(name, 'firebaseInstallations');
      return { doc: (id) => ({
        set: async (value) => { path = id; document = value; },
      }) };
    },
  };
  const repository = new FirebaseInstallationRepository(firestore);

  await repository.register({ sub: 'user1', fid: 'fid-1' });
  await repository.register({ sub: 'user2', fid: 'fid-1' });

  assert.equal(path, createHash('sha256').update('fid-1').digest('hex'));
  assert.deepEqual(document, { sub: 'user2', fid: 'fid-1', isActive: true });
});
