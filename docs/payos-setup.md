# payOS setup

The application supports real payOS payments without committing secrets to Git.

## Local setup

1. Copy `src/main/resources/application-vnpay-local.example.yaml` to `src/main/resources/application-vnpay-local.yaml`.
2. Fill in the real payOS values shared privately by the team:
   - `client-id`
   - `api-key`
   - `checksum-key`
3. Run the app on port `8083`.
4. In the booking payment step, choose `payOS / VietQR`.

`application-vnpay-local.yaml` is ignored by Git, so each developer keeps their own private copy.

## Deploy setup

On the hosting server, configure these environment variables instead of committing a secret file:

```env
PAYOS_ENABLED=true
PAYOS_CLIENT_ID=your-client-id
PAYOS_API_KEY=your-api-key
PAYOS_CHECKSUM_KEY=your-checksum-key
PAYOS_RETURN_URL=https://your-domain.com/payment/payos/return
PAYOS_CANCEL_URL=https://your-domain.com/payment/payos/cancel
```

For local testing, `PAYOS_RETURN_URL` and `PAYOS_CANCEL_URL` can use `http://localhost:8083`.
For deployment, they must use the public domain of the deployed app.
