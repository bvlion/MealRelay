'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const {
  completeAuthorization,
} = require('../src/auth');
const { AuthenticationError } = require('../src/errors');
const { authenticateMealRelayRequest } = require('../src/apiAuthentication');
const { FirestoreAuthRepository } = require('../src/firestoreAuthRepository');
const { getGoogleHealthOAuthClient } = require('../src/healthCredentials');

class MemoryFirestore {
  documents = new Map();

  collection(name) {
    return {
      doc: (id) => {
        const path = `${name}/${id}`;
        return {
          path,
          get: async () => this.snapshot(path),
        };
      },
    };
  }

  snapshot(path) {
    const data = this.documents.get(path);
    return { exists: data !== undefined, get: (key) => data?.[key] };
  }

  async runTransaction(action) {
    const pending = [];
    const transaction = {
      get: async (reference) => this.snapshot(reference.path),
      create: (reference, data) => pending.push(['create', reference.path, data]),
      update: (reference, data) => pending.push(['update', reference.path, data]),
    };
    const result = await action(transaction);
    for (const [operation, path, data] of pending) {
      if (operation === 'create') assert.equal(this.documents.has(path), false);
      this.documents.set(path, { ...this.documents.get(path), ...data });
    }
    return result;
  }
}

async function authorize(firestore, sub, email, refreshToken = 'refresh-token',
  allowedEmails = new Set(['user1@example.com', 'user2@example.com'])) {
  return completeAuthorization({
    code: 'one-time-google-code',
    googleOAuth: { exchangeCode: async () => ({ sub, email, refreshToken }) },
    repository: new FirestoreAuthRepository(firestore),
    allowedEmails,
  });
}

async function authenticate(firestore, token) {
  const repository = new FirestoreAuthRepository(firestore);
  const sub = await authenticateMealRelayRequest({ authorization: `Bearer ${token}`, repository });
  const oauthClient = await getGoogleHealthOAuthClient({
    sub, repository,
    createOAuthClient: () => ({ setCredentials(credentials) { this.credentials = credentials; } }),
  });
  return { sub, oauthClient };
}

test('two users and multiple devices use only their own Google Health credentials', async () => {
  const firestore = new MemoryFirestore();
  const firstToken = await authorize(firestore, 'google-sub-1', 'user1@example.com', 'refresh-1');
  const secondToken = await authorize(firestore, 'google-sub-2', 'user2@example.com', 'refresh-2');
  const replacementToken = await authorize(
    firestore, 'google-sub-1', 'new-address@example.com', null, new Set(),
  );

  const firstUser = await authenticate(firestore, firstToken);
  const secondUser = await authenticate(firestore, secondToken);
  assert.equal(firstUser.sub, 'google-sub-1');
  assert.equal(firstUser.oauthClient.credentials.refresh_token, 'refresh-1');
  assert.equal(secondUser.oauthClient.credentials.refresh_token, 'refresh-2');
  assert.notEqual(firstUser.oauthClient, secondUser.oauthClient);
  assert.equal((await authenticate(firestore, replacementToken)).oauthClient.credentials.refresh_token, 'refresh-1');
  assert.notEqual(firstToken, replacementToken);
  for (const token of [firstToken, secondToken, replacementToken]) {
    assert.equal([...firestore.documents.values()].some((data) => JSON.stringify(data).includes(token)), false);
  }
});

test('first registration rejects an email outside the allowlist', async () => {
  const firestore = new MemoryFirestore();
  await assert.rejects(
    authorize(firestore, 'new-sub', 'other@example.com'),
    (error) => error instanceof AuthenticationError && error.status === 403,
  );
  assert.equal(firestore.documents.size, 0);
});

test('new user must provide a refresh token', async () => {
  const firestore = new MemoryFirestore();
  await assert.rejects(authorize(firestore, 'google-sub-1', 'user1@example.com', null), AuthenticationError);
  assert.equal(firestore.documents.size, 0);
});

test('invalid MealRelay tokens cannot select a user', async () => {
  const firestore = new MemoryFirestore();
  const token = await authorize(firestore, 'google-sub-1', 'user1@example.com');
  await assert.rejects(authenticate(firestore, 'A'.repeat(43) === token ? 'B'.repeat(43) : 'A'.repeat(43)), AuthenticationError);
  await assert.rejects(authenticateMealRelayRequest({
    authorization: null, repository: new FirestoreAuthRepository(firestore),
  }), AuthenticationError);
  await assert.rejects(authenticateMealRelayRequest({
    authorization: `Basic ${token}`, repository: new FirestoreAuthRepository(firestore),
  }), AuthenticationError);
});

test('registered email cannot be claimed by another sub', async () => {
  const firestore = new MemoryFirestore();
  await authorize(firestore, 'google-sub-1', 'user1@example.com');
  await assert.rejects(
    authorize(firestore, 'google-sub-2', 'user1@example.com'),
    AuthenticationError,
  );
});

test('registration remains limited to two users after the allowlist changes', async () => {
  const firestore = new MemoryFirestore();
  await authorize(firestore, 'google-sub-1', 'user1@example.com');
  await authorize(firestore, 'google-sub-2', 'user2@example.com');
  await assert.rejects(
    authorize(
      firestore,
      'google-sub-3', 'user3@example.com', 'refresh-token',
      new Set(['user3@example.com', 'user4@example.com']),
    ),
    AuthenticationError,
  );
});
