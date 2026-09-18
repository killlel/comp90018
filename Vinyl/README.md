# Vinyl

Vinyl is an Android application for sharing, discovering, and collecting personalized vinyl cards with track previews and daily mood prompts.

## Project Structure

```
app/src/main/java/com/example/vinyl/
├── MainActivity.kt                # App entry point & main navigation graph
│
├── data/                          # Core Data Models & Backend Setup
│   ├── GoogleAuthRepository.kt   # Google Sign-In / Auth handling
│   ├── SupabaseClient.kt         # Supabase client setup
│   ├── Track.kt                  # Track data model
│   ├── MoodOptions.kt            # Mood selections & metadata
│   ├── VinylEnums.kt             # Shared enums
│   ├── model/
│   │   └── VinylRecord.kt        # Vinyl record entity model
│   └── repository/
│       ├── VinylRepository.kt    # Vinyl repository interface
│       └── FakeVinylRepository.kt# Mock implementation for local testing
│
├── repository/                    # Submission Repositories
│   ├── SubmissionRepository.kt    # Card submission interface/implementation
│   └── FakeSubmissionRepository.kt# Mock implementation
│
├── network/                       # Network & Media Services
│   ├── ITunesApiService.kt       # iTunes API for track search & preview URLs
│   └── AudioPreviewPlayer.kt     # MediaPlayer helper for 30s track previews
│
└── ui/                            # Presentation Layer (Compose Screens & ViewModels)
    ├── theme/                     # App Styling & Theme
    │   ├── Color.kt
    │   ├── Theme.kt
    │   ├── Type.kt
    │   └── VinylPalette.kt
    │
    ├── components/                # Reusable UI Components
    │   └── VinylSleeveThumbnail.kt# Custom vinyl sleeve thumbnail component
    │
    ├── write/                     # Feature: Compose & Send a Vinyl Card
    │   ├── WriteCardScreen.kt     # Screen for writing a card & picking music/mood
    │   ├── WriteCardViewModel.kt  # State management for card creation
    │   ├── WriteCardUiState.kt    # UI state definition
    │   └── SendingAnimation.kt    # Card sending animation
    │
    ├── daily/                     # Feature: Daily Records & Questionnaire
    │   ├── MoodQuestionnaireScreen.kt # Daily mood prompt
    │   ├── UnopenRecordScreen.kt # Screen for opening today's record
    │   └── ArrivedTodayScreen.kt # View record arrived today
    │
    ├── receive/                   # Feature: Receive / View Received Vinyls
    │   └── ReceivedCardScreen.kt  # Viewing incoming cards
    │
    └── collection/                # Feature: Collection Library
        ├── CollectionScreen.kt    # User's saved vinyl record collection
        ├── CollectionViewModel.kt # State management for collection screen
        └── CollectionUiState.kt   # UI state definition
```

## Tech Stack & Features
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Backend & Auth**: Supabase & Google Sign-In
- **Networking**: iTunes Search API integration
- **Audio Playback**: Android MediaPlayer for 30s track previews
- **Architecture**: MVVM with Repository Pattern
