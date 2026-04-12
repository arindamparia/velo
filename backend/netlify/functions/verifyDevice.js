const { neon } = require('@neondatabase/serverless');

/**
 * Netlify Serverless Function: verifyDevice
 * Endpoint: /verifyDevice
 */
exports.handler = async (event, context) => {
  if (event.httpMethod !== 'POST') {
    return {
      statusCode: 405,
      body: JSON.stringify({ error: 'Method Not Allowed. Use POST.' })
    };
  }

  try {
    const body = JSON.parse(event.body);
    const deviceId = body.device_id;
    const deviceName = body.device_name || 'Unknown Device';
    const osVersion = body.os_version || 'Unknown OS';

    if (!deviceId) {
      return {
        statusCode: 400,
        body: JSON.stringify({ error: 'Missing device_id in request body' })
      };
    }

    const sql = neon(process.env.DATABASE_URL);

    const existingDeviceQuery = await sql`
      SELECT is_active FROM devices WHERE device_id = ${deviceId} LIMIT 1
    `;

    if (existingDeviceQuery.length > 0) {
      const isActive = existingDeviceQuery[0].is_active;
      return {
        statusCode: 200,
        body: JSON.stringify({ is_active: isActive })
      };
    } else {
      await sql`
        INSERT INTO devices (device_id, device_name, os_version, is_active)
        VALUES (${deviceId}, ${deviceName}, ${osVersion}, true)
      `;
      return {
        statusCode: 200,
        body: JSON.stringify({ is_active: true })
      };
    }
  } catch (error) {
    console.error('Database configuration crash:', error);
    return {
      statusCode: 500,
      body: JSON.stringify({ error: 'Internal Server Error', details: error.message })
    };
  }
};
