package ru.nayanovaacademy.journal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ru.nayanovaacademy.journal.ui.classes.ClassesScreen
import ru.nayanovaacademy.journal.ui.classjournal.ClassJournalScreen
import ru.nayanovaacademy.journal.ui.day.DayScreen
import ru.nayanovaacademy.journal.ui.journal.JournalScreen
import ru.nayanovaacademy.journal.ui.lessons.LessonsScreen
import ru.nayanovaacademy.journal.ui.settings.SettingsScreen

object Routes {
    const val SETTINGS = "settings"
    const val DAY = "day"
    const val CLASSES = "classes"
    const val LESSONS = "lessons/{classId}/{subjectId}/{className}/{subjectName}"
    const val JOURNAL = "journal/{lessonId}"
    const val CLASS_JOURNAL = "class-journal/{classId}/{subjectId}/{className}/{subjectName}"

    fun lessons(classId: Int, subjectId: Int, className: String, subjectName: String): String {
        return "lessons/$classId/$subjectId/${java.net.URLEncoder.encode(className, "UTF-8")}/${java.net.URLEncoder.encode(subjectName, "UTF-8")}"
    }

    fun journal(lessonId: Int): String = "journal/$lessonId"

    fun classJournal(classId: Int, subjectId: Int, className: String, subjectName: String): String {
        return "class-journal/$classId/$subjectId/${java.net.URLEncoder.encode(className, "UTF-8")}/${java.net.URLEncoder.encode(subjectName, "UTF-8")}"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.SETTINGS) {
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.DAY) {
                        popUpTo(Routes.SETTINGS) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DAY) {
            DayScreen(
                onLessonSelected = { lessonId ->
                    navController.navigate(Routes.journal(lessonId))
                },
                onCreateLesson = {
                    navController.navigate(Routes.CLASSES)
                },
                onLogout = {
                    navController.navigate(Routes.SETTINGS) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.CLASSES) {
            ClassesScreen(
                onClassSelected = { classId, subjectId, className, subjectName ->
                    navController.navigate(Routes.lessons(classId, subjectId, className, subjectName))
                },
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.SETTINGS) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(
            Routes.LESSONS,
            arguments = listOf(
                navArgument("classId") { type = NavType.IntType },
                navArgument("subjectId") { type = NavType.IntType },
                navArgument("className") { type = NavType.StringType },
                navArgument("subjectName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getInt("classId") ?: 0
            val subjectId = backStackEntry.arguments?.getInt("subjectId") ?: 0
            val className = backStackEntry.arguments?.getString("className") ?: ""
            val subjectName = backStackEntry.arguments?.getString("subjectName") ?: ""
            LessonsScreen(
                classId = classId,
                subjectId = subjectId,
                className = className,
                subjectName = subjectName,
                onLessonSelected = { lessonId ->
                    navController.navigate(Routes.journal(lessonId))
                },
                onClassJournal = {
                    navController.navigate(Routes.classJournal(classId, subjectId, className, subjectName))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.JOURNAL,
            arguments = listOf(navArgument("lessonId") { type = NavType.IntType })
        ) { backStackEntry ->
            val lessonId = backStackEntry.arguments?.getInt("lessonId") ?: 0
            JournalScreen(
                lessonId = lessonId,
                onBack = { navController.popBackStack() },
                onClassJournal = { classId, subjectId, className, subjectName ->
                    navController.navigate(Routes.classJournal(classId, subjectId, className, subjectName))
                }
            )
        }
        composable(
            Routes.CLASS_JOURNAL,
            arguments = listOf(
                navArgument("classId") { type = NavType.IntType },
                navArgument("subjectId") { type = NavType.IntType },
                navArgument("className") { type = NavType.StringType },
                navArgument("subjectName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getInt("classId") ?: 0
            val subjectId = backStackEntry.arguments?.getInt("subjectId") ?: 0
            val className = backStackEntry.arguments?.getString("className") ?: ""
            val subjectName = backStackEntry.arguments?.getString("subjectName") ?: ""
            ClassJournalScreen(
                classId = classId,
                subjectId = subjectId,
                className = className,
                subjectName = subjectName,
                onLessonSelected = { lessonId ->
                    navController.navigate(Routes.journal(lessonId))
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
