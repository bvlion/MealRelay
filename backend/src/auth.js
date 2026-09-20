'use strict';

const { randomBytes } = require('node:crypto');
const { AuthenticationError } = require('./errors');

async function completeAuthorization({ code, googleOAuth, repository, allowedEmails }) {
  const identity = await googleOAuth.exchangeCode(code);
  const token = randomBytes(32).toString('base64url');
  let user = await repository.findUser(identity.sub);

  if (!user) {
    if (!allowedEmails.has(identity.email) || !identity.refreshToken) {
      throw new AuthenticationError('Google account is not eligible for registration', 403);
    }
    const result = await repository.registerNewUser({
      ...identity, token, maximumUsers: 2,
    });
    if (result === 'registered') return token;
    if (result === 'emailClaimed') {
      throw new AuthenticationError('Google account is already registered', 403);
    }
    if (result === 'full') throw new AuthenticationError('MealRelay registration is full', 403);
    user = await repository.findUser(identity.sub);
  }

  if (user?.sub !== identity.sub) {
    throw new AuthenticationError('Google identity does not match the registered user', 403);
  }
  if (!user.refreshToken && !identity.refreshToken) {
    throw new AuthenticationError('Google Health authorization is unavailable', 403);
  }
  const isIssued = await repository.issueTokenForUser({
    sub: identity.sub, refreshToken: identity.refreshToken, token,
  });
  if (!isIssued) throw new AuthenticationError('MealRelay registration is unavailable', 403);
  return token;
}

module.exports = { completeAuthorization };
