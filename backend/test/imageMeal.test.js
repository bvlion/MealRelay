'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const {
  IMAGE_ANALYSIS_MODEL,
  MAXIMUM_IMAGE_BYTES,
  analyzeMealImage,
  parseImageMealRequest,
} = require('../src/imageMeal');
const { handleImageMealRequest } = require('../src/imageMealEndpoint');

function multipartBody({ mealId = 'photo-1', capturedAt = '2026-09-21T12:30:00+09:00',
  image = Buffer.from('image'), mediaType = 'image/jpeg', extraParts = [] } = {}) {
  const boundary = 'meal-relay-boundary';
  const parts = [
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="mealId"\r\n\r\n${mealId}\r\n`),
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="capturedAt"\r\n\r\n${capturedAt}\r\n`),
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="meal.jpg"\r\n` +
      `Content-Type: ${mediaType}\r\n\r\n`),
    image,
    Buffer.from('\r\n'),
    ...extraParts,
    Buffer.from(`--${boundary}--\r\n`),
  ];
  return {
    boundary,
    rawBody: Buffer.concat(parts),
  };
}

function requestFixture(options) {
  const multipart = multipartBody(options);
  const headers = {
    authorization: 'Bearer token-1',
    'content-type': `multipart/form-data; boundary=${multipart.boundary}`,
  };
  return {
    method: 'POST',
    headers,
    rawBody: multipart.rawBody,
    get: (name) => headers[name.toLowerCase()],
    is: (mediaType) => mediaType === 'multipart/form-data',
  };
}

function responseFixture() {
  return {
    headers: {},
    statusCode: null,
    body: null,
    set(name, value) {
      this.headers[name] = value;
      return this;
    },
    status(statusCode) {
      this.statusCode = statusCode;
      return this;
    },
    json(body) {
      this.body = body;
      return this;
    },
    end() {
      return this;
    },
  };
}

function endpointFixture({ tokenOwner = 'user1' } = {}) {
  const calls = { analysis: [], googleHealth: [], tokenLookups: [] };
  const analysis = {
    foodDisplayName: 'ご飯 約150g、焼き鮭 約80g',
    estimated: {
      energyKcal: 450,
      proteinGrams: 28,
      carbohydrateGrams: 62,
      fatGrams: 10,
    },
    confirmed: {
      energyKcal: { value: 430, origin: 'packageLabel' },
    },
  };
  const analysisClient = {
    models: {
      generateContent: async (request) => {
        calls.analysis.push(request);
        return { text: JSON.stringify(analysis) };
      },
    },
  };
  const authRepository = {
    findSubByToken: async (token) => {
      calls.tokenLookups.push(token);
      return token === 'token-1' ? tokenOwner : null;
    },
    getRefreshToken: async (sub) => sub === tokenOwner ? 'refresh-token' : null,
  };
  let savedRecord;
  const mealRepository = {
    saveIfAbsent: async (record) => {
      savedRecord = record;
      return 'new';
    },
    markRegistered: async (record, name) => {
      savedRecord = { ...record, googleHealthName: name };
    },
  };
  const healthClient = {
    createDataPoint: async (request) => {
      calls.googleHealth.push(request);
      return [{ promise: async () => [{ name: request.dataPoint.name }] }];
    },
  };
  return {
    calls,
    dependencies: {
      analysisClient,
      authRepository,
      mealRepository,
      clientId: 'client-id',
      clientSecret: 'client-secret',
      healthClient,
    },
    savedRecord: () => savedRecord,
  };
}

test('multipart parser accepts one supported meal image and required metadata', async () => {
  const request = requestFixture({ image: Buffer.from([1, 2, 3]), mediaType: 'image/heic' });

  const parsed = await parseImageMealRequest(request);

  assert.equal(parsed.mealId, 'photo-1');
  assert.equal(parsed.capturedAt, '2026-09-21T12:30:00+09:00');
  assert.equal(parsed.image.mediaType, 'image/heic');
  assert.deepEqual(parsed.image.data, Buffer.from([1, 2, 3]));
});

test('multipart parser rejects images beyond the model inline limit', async () => {
  const request = requestFixture({ image: Buffer.alloc(MAXIMUM_IMAGE_BYTES + 1) });

  await assert.rejects(parseImageMealRequest(request), (error) => {
    assert.equal(error.status, 413);
    assert.match(error.message, /exceeds 7 MB/);
    return true;
  });
});

test('image analyzer uses the selected model, consumed portions, and structured output', async () => {
  let generatedRequest;
  const expected = {
    foodDisplayName: 'ピザ 約2切れ',
    estimated: { energyKcal: 520, proteinGrams: 22, carbohydrateGrams: 60, fatGrams: 22 },
    confirmed: {},
  };
  const client = {
    models: {
      generateContent: async (request) => {
        generatedRequest = request;
        return { text: JSON.stringify(expected) };
      },
    },
  };

  const actual = await analyzeMealImage({
    client,
    image: { data: Buffer.from('photo'), mediaType: 'image/jpeg' },
  });

  assert.deepEqual(actual, expected);
  assert.equal(generatedRequest.model, IMAGE_ANALYSIS_MODEL);
  assert.equal(generatedRequest.contents[0].inlineData.mimeType, 'image/jpeg');
  assert.equal(generatedRequest.contents[0].inlineData.data, Buffer.from('photo').toString('base64'));
  assert.equal(generatedRequest.config.responseMimeType, 'application/json');
  assert.match(generatedRequest.config.systemInstruction, /shared platter/);
  assert.match(generatedRequest.config.systemInstruction, /actually consumed/);
  assert.deepEqual(generatedRequest.config.responseJsonSchema.required,
    ['foodDisplayName', 'estimated', 'confirmed']);
});

test('image endpoint authenticates, analyzes, and registers one Google Health meal', async () => {
  const request = requestFixture();
  const response = responseFixture();
  const fixture = endpointFixture();

  await handleImageMealRequest({ request, response, ...fixture.dependencies });

  assert.equal(response.statusCode, 201);
  assert.equal(response.headers['Cache-Control'], 'no-store');
  assert.equal(response.body.isAlreadyRegistered, false);
  assert.equal(response.body.record.userId, 'user1');
  assert.equal(response.body.record.mealId, 'photo-1');
  assert.equal(response.body.record.eatenAt, '2026-09-21T03:30:00.000Z');
  assert.equal(response.body.record.foodDisplayName, 'ご飯 約150g、焼き鮭 約80g');
  assert.deepEqual(response.body.record.nutrition.energyKcal.confirmed,
    { value: 430, origin: 'packageLabel' });
  assert.deepEqual(fixture.calls.tokenLookups, ['token-1', 'token-1']);
  assert.equal(fixture.calls.analysis.length, 1);
  assert.equal(fixture.calls.googleHealth.length, 1);
  assert.deepEqual(fixture.calls.googleHealth[0].dataPoint.nutritionLog.energy, { kcal: 430 });
  assert.equal(fixture.savedRecord().userId, 'user1');
});

test('image endpoint rejects an unauthenticated request before image analysis', async () => {
  const request = requestFixture();
  request.headers.authorization = 'Bearer invalid';
  const response = responseFixture();
  const fixture = endpointFixture();

  await handleImageMealRequest({ request, response, ...fixture.dependencies });

  assert.equal(response.statusCode, 401);
  assert.deepEqual(response.body, { error: 'MealRelay authentication failed' });
  assert.equal(fixture.calls.analysis.length, 0);
  assert.equal(fixture.calls.googleHealth.length, 0);
});
