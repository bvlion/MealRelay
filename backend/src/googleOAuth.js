'use strict';

const { OAuth2Client } = require('google-auth-library');
const { AuthenticationError } = require('./errors');

const GOOGLE_HEALTH_WRITE_SCOPE = 'https://www.googleapis.com/auth/googlehealth.nutrition.writeonly';

function createGoogleOAuth(clientId, clientSecret) {
  const client = new OAuth2Client({ clientId, clientSecret });
  // Android server auth codes use an empty redirect_uri during the token exchange.
  // google-auth-library omits it unless redirectUri is explicitly set on the client.
  client.redirectUri = '';
  return client;
}

function createGoogleAuthorizationService(clientId, clientSecret) {
  const oauthClient = createGoogleOAuth(clientId, clientSecret);
  return {
    exchangeCode: (code) => exchangeGoogleAuthorizationCode({ code, oauthClient, clientId }),
  };
}

async function exchangeGoogleAuthorizationCode({ code, oauthClient, clientId }) {
  if (typeof code !== 'string' || !code) {
    throw new AuthenticationError('Invalid authorization code', 400);
  }

  let tokens;
  try {
    ({ tokens } = await oauthClient.getToken(code));
  } catch {
    throw new AuthenticationError('Google authorization code exchange failed');
  }
  if (!tokens.id_token || !tokens.access_token) {
    throw new AuthenticationError('Google authorization is incomplete');
  }
  let tokenInfo;
  try {
    tokenInfo = await oauthClient.getTokenInfo(tokens.access_token);
  } catch {
    throw new AuthenticationError('Google access token verification failed');
  }
  if (!tokenInfo.scopes.includes(GOOGLE_HEALTH_WRITE_SCOPE)) {
    throw new AuthenticationError('Required Google Health scopes were not granted', 403);
  }

  let sub;
  let claims;
  try {
    const ticket = await oauthClient.verifyIdToken({ idToken: tokens.id_token, audience: clientId });
    sub = ticket.getUserId();
    claims = ticket.getPayload();
  } catch {
    throw new AuthenticationError('Google identity verification failed');
  }
  if (!sub || !claims?.email || claims.email_verified !== true) {
    throw new AuthenticationError('Verified Google identity is required');
  }

  return {
    sub,
    email: claims.email.toLowerCase(),
    refreshToken: tokens.refresh_token,
  };
}

module.exports = {
  GOOGLE_HEALTH_WRITE_SCOPE,
  createGoogleAuthorizationService,
  createGoogleOAuth,
  exchangeGoogleAuthorizationCode,
};
