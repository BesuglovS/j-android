package com.nayanova.journal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nayanova.journal.ui.auth.LoginScreen
import com.nayanova.journal.ui.classes.ClassesScreen
import com.nayanova.journal.ui.journal.JournalScreen
import com.nayanova.journal.ui.lessons.LessonsScreen

object Routes {
    const val LOGIN = "login"
    const val CLASSES = "classes"
    const val LESSONS = "lessons/{classId}/{subjectId}/{className}/{subjectName}"
    const val JOURNAL = "journal/{lessonId}"

    fun lessons(classId: Int, subjectId: Int, className: String, subjectName: String): String {
        return "lessons/$classId/$subjectId/${java.net.URLEncoder.encode(className, "UTF-8")}/${java.net.URLEncoder.encode(subjectName, "UTF-8")}"
    }

    fun journal(lessonId: Int): String = "journal/$lessonId"
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.CLASSES) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.CLASSES) {
            ClassesScreen(
                onClassSelected = { classId, subjectId, className, subjectName ->
                    navController.navigate(Routes.lessons(classId, subjectId, className, subjectName))
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
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
                onBack = { navController.popBackStack() }
            )
        }
    }
}
