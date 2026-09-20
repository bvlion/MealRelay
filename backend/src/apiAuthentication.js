'use strict';

const authorizationHeader = require('auth-header');
const { AuthenticationError } = require('./errors');

async function authenticateMealRelayRequest({ authorization, repository }) {
  let credentials;
  try {
    credentials = authorizationHeader.parse(authorization);
  } catch {
    throw new AuthenticationError('MealRelay authentication is required');
  }
  if (credentials?.scheme.toLowerCase() !== 'bearer' || !credentials.token) {
    throw new AuthenticationError('MealRelay authentication is required');
  }
  const sub = await repository.findSubByToken(credentials.token);
  if (!sub) throw new AuthenticationError('MealRelay authentication failed');
  return sub;
}

module.exports = { authenticateMealRelayRequest };
