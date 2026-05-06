export interface InboundMessage {
  platform: string;
  userId: string;
  messageType: string;
  payload: Record<string, unknown>;
}

export class JavaClient {
  private readonly apiUrl: string;

  constructor() {
    this.apiUrl = process.env.JAVA_API_URL ?? "http://localhost:8080";
  }

  async forward(msg: InboundMessage): Promise<void> {
    const response = await fetch(`${this.apiUrl}/api/v1/messages`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(msg),
    });

    if (!response.ok) {
      throw new Error(
        `Java API responded with status ${response.status}: ${await response.text()}`
      );
    }
  }
}
