'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { FirebaseMealNotifier } = require('../src/firebaseMealNotifier');

test('sends registration details to every active installation using FID targets', async () => {
  const sent = [];
  const notifier = new FirebaseMealNotifier({
    installationRepository: {
      findActiveByUser: async (userId) => {
        assert.equal(userId, 'user1');
        return [{ id: 'hashed-fid-1', fid: 'fid-1' }, { id: 'hashed-fid-2', fid: 'fid-2' }];
      },
      deactivate: async () => assert.fail('No installation should be deactivated'),
    },
    messaging: { send: async (message) => sent.push(message) },
  });

  await notifier.notifyRegistration({ userId: 'user1', foodDisplayName: 'ご飯 約150g、焼き鮭 約80g' });

  assert.deepEqual(sent, [
    {
      fid: 'fid-1',
      notification: { title: '食事を記録しました', body: '以下を記録しました\n・ご飯 約150g\n・焼き鮭 約80g' },
    },
    {
      fid: 'fid-2',
      notification: { title: '食事を記録しました', body: '以下を記録しました\n・ご飯 約150g\n・焼き鮭 約80g' },
    },
  ]);
});

test('deactivates an installation that FCM identifies as no longer registered', async () => {
  const deactivated = [];
  const notifier = new FirebaseMealNotifier({
    installationRepository: {
      findActiveByUser: async () => [{ id: 'hashed-fid', fid: 'fid' }],
      deactivate: async (id) => deactivated.push(id),
    },
    messaging: { send: async () => { throw { code: 'messaging/installation-id-not-registered' }; } },
  });

  await notifier.notifyRegistration({ userId: 'user1', foodDisplayName: '食事' });

  assert.deepEqual(deactivated, ['hashed-fid']);
});
