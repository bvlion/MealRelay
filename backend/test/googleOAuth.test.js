'use strict';

const assert = require('node:assert/strict');
const { generateKeyPairSync, createSign } = require('node:crypto');
const test = require('node:test');
const { AuthenticationError } = require('../src/errors');
const {
  GOOGLE_HEALTH_WRITE_SCOPE,
  GOOGLE_HEALTH_READ_SCOPE,
  createGoogleOAuth,
  exchangeGoogleAuthorizationCode,
} = require('../src/googleOAuth');

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });

function signedIdToken(claims) {
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', kid: 'test-key' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({
    iss: 'https://accounts.google.com', aud: 'web-client-id', sub: 'google-sub-1',
    email: 'USER1@example.com', email_verified: true, iat: now, exp: now + 3600,
    ...claims,
  })).toString('base64url');
  const signature = createSign('RSA-SHA256').update(`${header}.${payload}`).sign(privateKey).toString('base64url');
  return `${header}.${payload}.${signature}`;
}

function googleClient(tokens, grantedScopes = `openid email ${GOOGLE_HEALTH_WRITE_SCOPE} ${GOOGLE_HEALTH_READ_SCOPE}`) {
  const client = createGoogleOAuth('web-client-id', 'test-secret');
  client.certificateCache = { 'test-key': publicKey.export({ type: 'spki', format: 'pem' }) };
  client.certificateExpiry = new Date(Date.now() + 3600000);
  let requestBody;
  client.transporter = {
    request: async (options) => {
      if (options.url.includes('tokeninfo')) {
        return { data: { scope: grantedScopes, expires_in: 3600 } };
      }
      requestBody = options.data;
      return { data: tokens };
    },
  };
  return { client, getRequestBody: () => requestBody };
}

test('Android server auth code sends an empty redirect URI and verifies the signed ID token', async () => {
  const { client, getRequestBody } = googleClient({
    id_token: signedIdToken(), access_token: 'access-token', refresh_token: 'refresh-token',
  }, `openid https://www.googleapis.com/auth/userinfo.email ${GOOGLE_HEALTH_WRITE_SCOPE} ${GOOGLE_HEALTH_READ_SCOPE}`);
  const identity = await exchangeGoogleAuthorizationCode({ code: 'server-auth-code', oauthClient: client, clientId: 'web-client-id' });
  assert.deepEqual(identity, {
    sub: 'google-sub-1', email: 'user1@example.com', refreshToken: 'refresh-token',
  });
  assert.equal(getRequestBody().has('redirect_uri'), true);
  assert.equal(getRequestBody().get('redirect_uri'), '');
  assert.equal(getRequestBody().get('code'), 'server-auth-code');
});

test('missing granted scope and invalid signed identities are rejected by the real OAuth client', async () => {
  const validToken = signedIdToken();
  const [header, payload, signature] = validToken.split('.');
  const tamperedSignature = `${header}.${payload}.${signature[0] === 'A' ? 'B' : 'A'}${signature.slice(1)}`;
  for (const [idToken, grantedScopes] of [
    [signedIdToken(), 'openid email'],
    [signedIdToken(), `openid email ${GOOGLE_HEALTH_WRITE_SCOPE}`],
    [signedIdToken(), `openid email ${GOOGLE_HEALTH_READ_SCOPE}`],
    [tamperedSignature, `openid email ${GOOGLE_HEALTH_WRITE_SCOPE} ${GOOGLE_HEALTH_READ_SCOPE}`],
    [signedIdToken({ aud: 'another-client' }), `openid email ${GOOGLE_HEALTH_WRITE_SCOPE} ${GOOGLE_HEALTH_READ_SCOPE}`],
    [signedIdToken({ iss: 'another-issuer' }), `openid email ${GOOGLE_HEALTH_WRITE_SCOPE} ${GOOGLE_HEALTH_READ_SCOPE}`],
    [signedIdToken({ exp: 1 }), `openid email ${GOOGLE_HEALTH_WRITE_SCOPE} ${GOOGLE_HEALTH_READ_SCOPE}`],
    [signedIdToken({ email_verified: false }), `openid email ${GOOGLE_HEALTH_WRITE_SCOPE} ${GOOGLE_HEALTH_READ_SCOPE}`],
  ]) {
    const { client } = googleClient({ id_token: idToken, access_token: 'access-token' }, grantedScopes);
    await assert.rejects(
      exchangeGoogleAuthorizationCode({ code: 'server-auth-code', oauthClient: client, clientId: 'web-client-id' }),
      AuthenticationError,
    );
  }
});
