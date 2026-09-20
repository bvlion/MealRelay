'use strict';

const { AuthenticationError } = require('./errors');

async function getGoogleHealthOAuthClient({ sub, repository, createOAuthClient }) {
  const refreshToken = await repository.getRefreshToken(sub);
  if (typeof refreshToken !== 'string' || !refreshToken) {
    throw new AuthenticationError('Google Health authorization is unavailable', 403);
  }
  const oauthClient = createOAuthClient();
  oauthClient.setCredentials({ refresh_token: refreshToken });
  return oauthClient;
}

module.exports = { getGoogleHealthOAuthClient };
