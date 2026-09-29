# Contributing to CivilEG 2

Thank you for your interest in contributing to CivilEG 2! This document provides guidelines for contributing to this Android civil engineering design application.

## Getting Started

1. **Fork** the repository
2. **Clone** your fork locally
3. **Create a branch** for your feature or fix: `git checkout -b feature/your-feature`
4. **Make your changes** following the coding standards below
5. **Test** your changes thoroughly
6. **Commit** with clear, descriptive messages
7. **Push** to your fork and open a Pull Request

## Project Structure

```
app/src/main/java/com/civileg/app/
├── domain/           # Business logic (calculations, entities, use cases)
│   ├── calculations/  # Design code implementations (ECP, ACI, SBC, AISC)
│   │   ├── base/       # Abstract base classes
│   │   ├── ecp/        # Egyptian Code of Practice
│   │   ├── aci/        # ACI 318
│   │   ├── sbc/        # Saudi Building Code
│   │   └── utils/      # Shared calculation utilities
│   ├── entities/       # Data classes (SteelEntities, ReinforcementResult, etc.)
│   ├── usecases/       # Use case classes (CalculateElementBoq, AnalyzeRebarInventory)
│   └── validators/     # Input validation
├── ui/                # UI layer
│   ├── compose/        # Jetpack Compose screens and components
│   │   ├── screens/    # Full screen composables
│   │   └── components/ # Reusable UI components and drawings
│   └── [element]/      # Legacy View-based UI (being migrated)
├── viewmodel/         # MVVM ViewModels
├── db/                # Room database (DAOs, entities)
├── di/                # Hilt dependency injection modules
├── utils/             # Utilities (PDF generation, export, calculation helpers)
└── security/          # App integrity and encryption
```

## Coding Standards

- **Language**: Kotlin (primary), follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Architecture**: MVVM with Clean Architecture layers
- **UI**: Jetpack Compose (migrate legacy Views when possible)
- **DI**: Hilt for dependency injection
- **Database**: Room for local persistence
- **Language Support**: All user-facing strings must be bilingual (Arabic + English)
- **Engineering Units**: Always use consistent units (mm for dimensions, m for spans/lengths, MPa/MPa for stresses)

## Design Codes Supported

| Code | Status | Description |
|------|--------|-------------|
| ECP 203/205 | ✅ Active | Egyptian Code of Practice for Concrete/Steel |
| ACI 318 | ✅ Active | American Concrete Institute |
| SBC 304 | ✅ Active | Saudi Building Code |
| AISC 360 | ✅ Active | American Institute of Steel Construction |

## Pull Request Guidelines

- Keep PRs focused on a single concern
- Include a clear description of the change
- Reference any related issues
- Ensure all new text is bilingual (Arabic + English)
- Test calculations against known hand-calculated examples
- Do not modify build configuration unless necessary

## Reporting Bugs

When reporting bugs, please include:
1. Device and Android version
2. App version
3. Steps to reproduce
4. Expected vs actual behavior
5. Screenshots if applicable

## Development Setup

1. Android Studio Koala or newer
2. Gradle 8.x with AGP 8.x
3. Min SDK: 24, Target SDK: 34
4. Kotlin DSL for Gradle scripts

Thank you for helping make CivilEG 2 better!