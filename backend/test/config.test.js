'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { loadAuthenticationConfig } = require('../src/config');

test('configured allowed email addresses are normalized before registration matching', () => {
  const config = loadAuthenticationConfig({
    MEAL_RELAY_ALLOWED_EMAILS: '["User1@example.com","USER2@example.com"]',
    GOOGLE_OAUTH_CLIENT_ID: 'web-client-id',
    GOOGLE_OAUTH_CLIENT_SECRET: 'test-secret',
  });
  assert.deepEqual(config.allowedEmails, new Set(['user1@example.com', 'user2@example.com']));
});
