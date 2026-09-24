'use strict';

const TITLE = '食事を記録しました';

class FirebaseMealNotifier {
  constructor({ installationRepository, messaging }) {
    this.installationRepository = installationRepository;
    this.messaging = messaging;
  }

  async notifyRegistration(record) {
    const installations = await this.installationRepository.findActiveByUser(record.userId);
    const foodItems = record.foodDisplayName.split(/[、\n]/).map((item) => item.trim()).filter(Boolean);
    const body = `以下を記録しました\n${foodItems.map((item) => `・${item}`).join('\n')}`;
    const results = await Promise.allSettled(installations.map(async ({ id, fid }) => {
      try {
        await this.messaging.send({
          fid,
          notification: { title: TITLE, body },
        });
      } catch (error) {
        if (error.code === 'messaging/installation-id-not-registered') {
          await this.installationRepository.deactivate(id);
          return;
        }
        throw error;
      }
    }));
    const failedSend = results.find((result) => result.status === 'rejected');
    if (failedSend) throw failedSend.reason;
  }
}

module.exports = { FirebaseMealNotifier };
