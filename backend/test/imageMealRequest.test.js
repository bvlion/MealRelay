'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { parseImageMealRequest } = require('../src/imageMealRequest');

function multipartRequest({ mealId = 'photo-1', capturedAt = '2026-09-21T12:30:00+09:00',
  image = Buffer.from('image'), mediaType = 'image/jpeg' } = {}) {
  const boundary = 'meal-relay-boundary';
  const body = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="mealId"\r\n\r\n${mealId}\r\n`),
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="capturedAt"\r\n\r\n${capturedAt}\r\n`),
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="meal"\r\n` +
      `Content-Type: ${mediaType}\r\n\r\n`),
    image,
    Buffer.from(`\r\n--${boundary}--\r\n`),
  ]);
  return {
    headers: { 'content-type': `multipart/form-data; boundary=${boundary}` },
    rawBody: body,
  };
}

test('multipart parser returns one OpenAI-supported image and metadata', async () => {
  const request = multipartRequest({ image: Buffer.from([1, 2, 3]), mediaType: 'image/webp' });

  const parsed = await parseImageMealRequest(request);

  assert.equal(parsed.mealId, 'photo-1');
  assert.equal(parsed.capturedAt, '2026-09-21T12:30:00+09:00');
  assert.equal(parsed.image.mediaType, 'image/webp');
  assert.deepEqual(parsed.image.data, Buffer.from([1, 2, 3]));
});

test('multipart parser rejects an image format outside the OpenAI input specification', async () => {
  await assert.rejects(
    parseImageMealRequest(multipartRequest({ mediaType: 'image/heic' })),
    (error) => error.status === 415,
  );
});
