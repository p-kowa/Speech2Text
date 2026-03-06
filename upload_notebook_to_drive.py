#!/usr/bin/env python3
"""
Upload Voice_Cloning_Training.ipynb to Google Drive
This script uploads the notebook directly to your Google Drive.
"""

import os
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
import pickle

# If modifying these scopes, delete the token.pickle file.
SCOPES = ['https://www.googleapis.com/auth/drive.file']

def authenticate():
    """Authenticate with Google Drive"""
    creds = None

    # Token file stores the user's access and refresh tokens
    if os.path.exists('token.pickle'):
        with open('token.pickle', 'rb') as token:
            creds = pickle.load(token)

    # If there are no (valid) credentials available, let the user log in
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow = InstalledAppFlow.from_client_secrets_file(
                'credentials.json', SCOPES)
            creds = flow.run_local_server(port=0)

        # Save the credentials for the next run
        with open('token.pickle', 'wb') as token:
            pickle.dump(creds, token)

    return creds

def upload_notebook():
    """Upload the notebook to Google Drive"""
    creds = authenticate()
    service = build('drive', 'v3', credentials=creds)

    # Check if notebook exists
    notebook_path = 'Voice_Cloning_Training.ipynb'
    if not os.path.exists(notebook_path):
        print(f"❌ Error: {notebook_path} not found!")
        return

    # Check if file already exists in Drive
    results = service.files().list(
        q=f"name='{notebook_path}' and trashed=false",
        spaces='drive',
        fields='files(id, name)'
    ).execute()

    files = results.get('files', [])

    file_metadata = {
        'name': notebook_path,
        'mimeType': 'application/vnd.google.colaboratory'
    }

    media = MediaFileUpload(
        notebook_path,
        mimetype='application/x-ipynb+json',
        resumable=True
    )

    if files:
        # Update existing file
        file_id = files[0]['id']
        print(f"📝 Updating existing notebook in Google Drive...")

        file = service.files().update(
            fileId=file_id,
            media_body=media
        ).execute()

        print(f"✅ Notebook updated successfully!")
        print(f"   File ID: {file.get('id')}")
        print(f"\n🔗 Open in Colab:")
        print(f"   https://colab.research.google.com/drive/{file.get('id')}")
    else:
        # Create new file
        print(f"📤 Uploading notebook to Google Drive...")

        file = service.files().create(
            body=file_metadata,
            media_body=media,
            fields='id'
        ).execute()

        print(f"✅ Notebook uploaded successfully!")
        print(f"   File ID: {file.get('id')}")
        print(f"\n🔗 Open in Colab:")
        print(f"   https://colab.research.google.com/drive/{file.get('id')}")

if __name__ == '__main__':
    try:
        upload_notebook()
    except Exception as e:
        print(f"❌ Error: {e}")
        print("\n💡 Make sure you have:")
        print("   1. Created a Google Cloud project")
        print("   2. Enabled Google Drive API")
        print("   3. Downloaded credentials.json")
        print("   4. Installed: pip install google-auth google-auth-oauthlib google-api-python-client")

